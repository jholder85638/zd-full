#!/usr/bin/perl
# 
# 
# 

use strict;
use Migrate;

Migrate::verifySchemaVersion(22);

my @mailboxIds = Migrate::getMailboxIds();
addImapSyncColumn();
foreach my $id (@mailboxIds) {
    addImapIdColumn($id);
}

Migrate::updateSchemaVersion(22, 23);

exit(0);

#############

sub addImapSyncColumn()
{
    my $sql = <<ADD_TRACKING_IMAP_COLUMN_EOF;
ALTER TABLE zimbra.mailbox
MODIFY tracking_sync INTEGER UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE zimbra.mailbox
ADD COLUMN tracking_imap BOOLEAN NOT NULL DEFAULT 0 AFTER tracking_sync;

ADD_TRACKING_IMAP_COLUMN_EOF

    Migrate::log("Adding zimbra.mailbox.tracking_imap.");
    Migrate::runSql($sql);
}

sub addImapIdColumn($)
{
    my ($mailboxId) = @_;
    my $dbName = "mailbox" . $mailboxId;
	
    my $sql = <<ADD_IMAP_ID_COLUMN_EOF;
ALTER TABLE $dbName.mail_item
ADD COLUMN imap_id INTEGER UNSIGNED AFTER folder_id;

UPDATE $dbName.mail_item
SET imap_id = id WHERE type IN (5, 6, 8, 11, 14);

ADD_IMAP_ID_COLUMN_EOF

    Migrate::log("Adding and setting $dbName.mail_item.imap_id.");
    Migrate::runSql($sql);
}

