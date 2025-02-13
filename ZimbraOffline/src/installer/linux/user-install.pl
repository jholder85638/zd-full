#!/usr/bin/perl

#/*
# * 
# */
#
# ZD installer for user data files
#

use strict;
use warnings;

my $locale = "en_US";
my $home_dir = $ENV{HOME} || die("Error: unable to get user home directory");
my $app_root;
my $data_root;
my $tmpdir;
my $default_data_root = "$home_dir/zdesktop";

my $messages = {
    en_US => {
        ChooseDataRoot => "Choose the folder where you would like to install Zimbra Desktop's user data files, full path please",
        ConfirmDataRoot => "Are you sure you would like to install Zimbra Desktop's user data under folder [{0}] ? NOTE: All non-ZD data under this directory will be deleted.",
        ChooseIconDir => "Choose the folder where you would like to create desktop icon",
        Configuring => "Initializing user data...",
        CreateIcon => "Creating desktop icon...",
		Installing => "Installing user data files...",
        InvalidDataRoot1 => "*** Error: User data directory can not be the same as, or a subdirectory of, the application directory.",
        InvalidDataRoot2 => "*** Error: User data directory can not be a parent directory of the application directory.",
        InvalidDataRoot3 => "*** Error: User data directory can not be your home directory.",
        Done => "done",
        RunCommand => "You can start Zimbra Desktop by double-clicking the desktop icon or by running the following command:",
		RunWithAbsPath => '*** Error: You must run user-install.pl with absolute path.',
        Success => 'Zimbra Desktop has been installed successfully for user {0}.',
        LaunchZD => 'Press "Enter" to launch Zimbra Desktop; Press "Ctrl-c" to exit: ',
        YesNo => "(Y)es or (N)o"
    }
};

sub get_message($;$) {
    my ($key, $vars) = @_;

    my $msgs = $messages->{$locale};
    $msgs = $messages->{'en_US'} unless ($msgs);
    my $msg = $msgs->{$key};
    return '' unless ($msg);
    
    if ($vars) {
        my $c = 0;
        for my $v (@$vars) {
            my $k = '{' . $c . '}';
            my $pos = index($msg, $k);
            substr($msg, $pos, length($k)) = $v if ($pos >= 0);
            $c++;
        }
    }
    return $msg;
}

sub get_input($;$$) {
    my ($prompt, $default, $allow_null) = @_;
    my $ret = '';

    print "------------------------------\n";
    while (!$ret) {
	print $prompt;
	print " [$default]" if ($default);
	print ": ";

	$| = 1;
	$_ = <STDIN>;
	chomp();
	$ret = $default ? ($_ ? $_ : $default) : $_;
	last if ($allow_null);
    }
    print "\n\n";
    return $ret;
}

sub find_and_replace($$) {
    my ($file, $tokens) = @_;
    my $tmpfile = $file . '.tmp';
    
    open(FIN, "<$file") or die("Error: cannot open file $file\n");
    open(FOUT, ">$tmpfile") or die("Error: cannot open file $tmpfile\n");
    
    my $line;
    while($line = <FIN>){
        foreach my $key (keys(%$tokens)) {
            my $pos = index($line, $key);
            while ($pos >= 0) {
                substr($line, $pos, length($key), $tokens->{$key});    
                $pos = index($line, $key);   
            }
        }
        print FOUT $line;
    }
    
    close FIN;
    close FOUT;

    my (undef, undef, $mode) = stat($file);
    unlink $file;
    rename $tmpfile, $file;
    chmod $mode, $file;
}

sub get_random_id() {
    my @n;
    srand(time ^ ($$ + ($$ << 15)) ^ int(rand(0xFFFF)));
    push(@n, sprintf("%04x", int(rand(0xFFFF)))) for (0..7);
    return "$n[0]$n[1]-$n[2]-$n[3]-$n[4]-$n[5]$n[6]$n[7]";
}

sub move_no_overwrite($$) {
	my ($src, $dest) = @_;
	my @files;
	
	if (! opendir(DH, $src)) {
		print "Unable to open directory $src\n";
		return;
	}
	@files = readdir(DH);
	closedir(DH);

	foreach my $file (@files) {
		next if ( $file eq "." || $file eq ".." );
		unless ( -e "$dest/$file") {
			system("mv \"$src/$file\" \"$dest\"");
		}
	}
}

sub dialog_data_root() { 
    my $dr;

    while (1) {   
        $dr = get_input(get_message("ChooseDataRoot"), "$home_dir/zdesktop");
        if (index($dr, $app_root) >= 0) {
            print get_message("InvalidDataRoot1"), "\n";
        } elsif (index($app_root, $dr) >= 0) {
            print get_message("InvalidDataRoot2"), "\n";
        } elsif (index($home_dir, $dr) >= 0) {
            print get_message("InvalidDataRoot3"), "\n";
        } else {
            return $dr;
        }
    }
}

sub dialog_confirm_data_root() {
    if ($data_root ne $default_data_root) {
        print get_message('ConfirmDataRoot', [$data_root]), "\n";
        my $in = lc(get_input(get_message('YesNo'), 'Y'));
        exit 1 if (substr($in, 0, 1) ne 'y');
    }
}

sub dialog_desktop_icon() { 
    return get_input(get_message("ChooseIconDir"), "$home_dir/Desktop");
}

# This will be only used when upgrading from 7.2 to 7.3
sub upgradePreferences() {
    my $src_file = "$tmpdir/profile/prefs.js";
    my $dest_folder = "$data_root/conf";
    my $dest_file = "$dest_folder/local_prefs.json";
    my $pref = "";

    # No existing preferences found
    if (!-e $src_file) {
        return;
    }

    if(!-e $dest_folder) {
        return;
    }

    # Read existing prism preference from tmp directory
    my %attrs;
    open(my $fh1, '<', $src_file) or die "Could not open file '$src_file' $!";
    my $cnt =0 ;
    while(my $row = <$fh1>) {
        chomp $row;
        if ($row =~ m/intl.accept_languages/ || $row =~ m/app.update.channel/) {
            my @parts = split /[(),\s\";]+/, $row;
            $attrs{$parts[1]} = $parts[2];
            $cnt++;
        }
    }
    close $fh1;

    # If user preferences are present then write them to new system in JSON format
    if ($cnt > 0) {
        my $content = "{";
        for my $attribute (keys %attrs) {
            my $val = $attrs{$attribute};
            if ($attribute =~ m/intl.accept_languages/) {
                #replace - with _ in locale
                $val =~ s/-/_/g;
                $attribute = "LOCALE_NAME";
            }
            if ($attribute =~ m/app.update.channel/) {
                $attribute = "AUTO_UPDATE_NOTIFICATION";
            }
            $content = $content . "\"$attribute\":\"$val\",";
        }
        $content = substr $content, 0, length($content) - 1;
        $content = $content . "}";
        open(my $fh2, '>', $dest_file) or die "Could not open file '$dest_file' $!";
        print $fh2 $content;
        close $fh2;
    }
}

# main
my $icon_dir;

my $script_path = $0;
if ($script_path eq 'user-install.pl' || $script_path eq './user-install.pl') {
	$script_path = `pwd`;
	chomp($script_path);
	$script_path .= '/user-install.pl';
}

unless ($script_path =~ /^\/.+/) {
	print get_message('RunWithAbsPath'), "\n";
	exit 1;
}

$app_root = substr($script_path, 0, length($script_path) - 22); # 22: "/linux/user-install.pl"
chdir($app_root);

$data_root = dialog_data_root();
dialog_confirm_data_root();
$icon_dir = dialog_desktop_icon();

$tmpdir = "$data_root" . ".tmp";
my @user_files = ("index", "store", "sqlite", "log", "zimlets-properties", "zimlets-deployed",
    "conf/keystore", "conf/local_prefs.json", "profile/persdict.dat", "profile/localstore.json");

my $is_upgrade = 0;
if (-e $data_root) {
	$is_upgrade = 1;	

	# backup user data	
    mkdir($tmpdir);
    system("rm -rf \"$tmpdir/*\"");
    mkdir("$tmpdir/profile");
    mkdir("$tmpdir/conf");

    for (@user_files) {
        my $src = "$data_root/$_";
        system("mv -f \"$src\" \"$tmpdir/$_\"") if (-e $src);
    }

    # Copy prism preference file, we will not add it to aUserFiles as
    # we don't want to restore the same file instead we will convert it to NWJS system
    system("mv -f \"$data_root/profile/prefs.js\" \"$tmpdir/profile/prefs.js\"") if (-e "$data_root/profile/prefs.js");

    system("rm -rf \"$data_root\"");
}

# copy files;
print "\n", get_message('Installing');
exit 1 if system("mkdir -p \"$data_root\"");
exit 1 if system("cp -r -p ./data/* \"$data_root\"");
print get_message('Done'), "\n";

my $tokens = {
	'@install.app.root@' => $app_root, 
	'@install.data.root@' => $data_root,
	'@install.key@' => get_random_id(),
	'@install.locale@' => 'en-US',
	'#@install.linux.java.home@' => "JAVA_HOME=\"$app_root/linux/jre\"",
	'@install.platform@' => 'Linux'
	
};

# fix data files
print get_message('Configuring');
find_and_replace("$data_root/conf/localconfig.xml", $tokens);
find_and_replace("$data_root/jetty/etc/jetty.xml", $tokens);
find_and_replace("$data_root/bin/zdesktop", $tokens);
find_and_replace("$app_root/linux/zdrun.pl", $tokens);
print get_message('Done'), "\n";

# create desktop icon
print get_message('CreateIcon');
exit 1 if system("cp -f -p \"$app_root/linux/zd.desktop\" \"$icon_dir\"");
find_and_replace("$icon_dir/zd.desktop", $tokens);
print get_message('Done'), "\n";

if ($is_upgrade) {

    # handle preferences seperately when upgrading from 7.2 (prism) to 7.3 (nwjs)
    upgradePreferences();

	for (@user_files) {
        my $src = "$tmpdir/$_";
        next if (! -e $src);

        my $dest = "$data_root/$_";
        if ((-d $src) && (-e $dest)) {
            system("mv -f \"$src\"/* \"$dest\""); # must move '/*' outside the quote
        } else {
            system("mv -f \"$src\" \"$dest\"");
        }
    }
	
    system("rm -rf \"$tmpdir\"");
}

system("chmod 700 \"$data_root\"");

print get_message('Success', [$ENV{USER}]), "\n\n";
print get_message('RunCommand'), "\n";
my $cmd = "\"$app_root/linux/zdrun.pl\"";
print "$cmd\n\n";

print get_message('LaunchZD');
$_ = <STDIN>;
my $pid = fork();
if ($pid == 0) {
    exec($cmd);
}
