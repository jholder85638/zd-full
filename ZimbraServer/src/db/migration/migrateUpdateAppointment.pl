#!/usr/bin/perl
# 
# 
# 

use strict;

#############

my $MYSQL = "mysql";
my $ROOT_USER = "root";
my $ROOT_PASSWORD = "liquid";
my $LIQUID_USER = "liquid";
my $LIQUID_PASSWORD = "liquid";
my $PASSWORD = "liquid";
my $DATABASE = "liquid";

#############

my @mailboxIds = runSql($LIQUID_USER,
			$LIQUID_PASSWORD,
			"SELECT id FROM mailbox ORDER BY id");

printLog("Found " . scalar(@mailboxIds) . " mailbox databases.");

my $id;
foreach $id (@mailboxIds) {
    recreateAppointment($id);
}

exit(0);

#############

sub recreateAppointment()
{
    my ($mailboxId) = @_;
    my $dbName = "mailbox" . $mailboxId;

    my $sql = <<RECREATE_APPT_EOF;

DROP TABLE $dbName.appointment;
    
CREATE TABLE IF NOT EXISTS $dbName.appointment
    (
     uid         VARCHAR(255) NOT NULL,
     item_id     INTEGER UNSIGNED NOT NULL,
     start_time  DATETIME NOT NULL,
     end_time    DATETIME,
     
     PRIMARY KEY (uid),
     INDEX i_item_id (item_id),
     CONSTRAINT fk_item_id FOREIGN KEY (item_id) REFERENCES $dbName.mail_item(id) ON DELETE CASCADE
     ) ENGINE = InnoDB;

RECREATE_APPT_EOF

    printLog("Updating appointment table.");
    runSql($ROOT_USER, $ROOT_PASSWORD, $sql);
}
    
sub runSql($$$)
{
    my ($user, $password, $script) = @_;

    # Write the last script to a text file for debugging
    # open(LASTSCRIPT, ">lastScript.sql") || die "Could not open lastScript.sql";
    # print(LASTSCRIPT $script);
    # close(LASTSCRIPT);

    # Run the mysql command and redirect output to a temp file
    my $tempFile = "mysql.out";
    my $command = "$MYSQL --user=$user --password=$password " .
        "--database=$DATABASE --batch --skip-column-names";
    open(MYSQL, "| $command > $tempFile") || die "Unable to run $command";
    print(MYSQL $script);
    close(MYSQL);

    if ($? != 0) {
        die "Error while running '$command'.";
    }

    # Process output
    open(OUTPUT, $tempFile) || die "Could not open $tempFile";
    my @output;
    while (<OUTPUT>) {
        s/\s+$//;
        push(@output, $_);
    }

    return @output;
}

sub printLog
{
    print scalar(localtime()), ": ", @_, "\n";
}
