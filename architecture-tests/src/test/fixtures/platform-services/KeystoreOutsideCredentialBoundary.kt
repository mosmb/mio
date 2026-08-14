package io.morgan.mio.data.media

import java.security.KeyStore

class KeystoreOutsideCredentialBoundary {
    val type = KeyStore.getDefaultType()
}
