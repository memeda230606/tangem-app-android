package com.niubtmd.securenfc;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** Reads and writes share 2 only through NTAG 424 DNA Full secure messaging. */
public final class RecoveryCardStore {
    private RecoveryCardStore() {}

    public static void write(Ntag424Dna card, Ntag424Dna.Session session,
        RecoverySecretSharing.Share cardShare) throws IOException, GeneralSecurityException {
        if (card == null || session == null) throw new IllegalArgumentException("Authenticated card is required");
        if (cardShare == null || cardShare.getIndex() != 2) {
            throw new IllegalArgumentException("Only card share 2 can be stored on the card");
        }
        card.writeFull(session, Ntag424Dna.RECOVERY_FILE, RecoveryCardRecord.encode(cardShare));
    }

    public static RecoverySecretSharing.Share read(Ntag424Dna card, Ntag424Dna.Session session)
        throws IOException, GeneralSecurityException {
        if (card == null || session == null) throw new IllegalArgumentException("Authenticated card is required");
        RecoverySecretSharing.Share share = RecoveryCardRecord.decode(
            card.readFull(session, Ntag424Dna.RECOVERY_FILE, RecoveryCardRecord.ENCODED_BYTES)
        );
        if (share.getIndex() != 2) throw new GeneralSecurityException("Card contains an invalid recovery share");
        return share;
    }
}
