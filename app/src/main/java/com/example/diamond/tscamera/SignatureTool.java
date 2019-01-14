package com.example.diamond.tscamera;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

class SignatureTool {

    private static final String ALGOLITHM ="SHA256withRSA";
    private static final String KeyProvider = "AndroidKeyStore";
    private static final String KeyAlias = "TimeStamp";

    private KeyStore keyStore;


     byte[] SIGN(byte[] bytes){
        try{
            if (keyStore == null) {
                keyStore = KeyStore.getInstance(KeyProvider);
                keyStore.load(null);
            }

            PrivateKey privateKey;
            if (!keyStore.containsAlias(KeyAlias)) {
                createNewKeyPair();
            }

            privateKey = (PrivateKey) keyStore.getKey(KeyAlias, null);

            Signature signature = Signature.getInstance(ALGOLITHM);
            signature.initSign(privateKey);
            signature.update(bytes);

            bytes = signature.sign();



        } catch (SignatureException | UnrecoverableEntryException | NoSuchAlgorithmException |
                CertificateException | KeyStoreException | InvalidKeyException |
                InvalidAlgorithmParameterException | IOException | NoSuchProviderException e) {
            e.printStackTrace();
        }
        return bytes;
    }


     boolean VERIFY(byte[] bytes){
        boolean result = false;
        try{
            if (keyStore == null) {
                keyStore = KeyStore.getInstance(KeyProvider);
                keyStore.load(null);
            }

            PublicKey publicKey;
            if (keyStore.containsAlias(KeyAlias)){
                publicKey = (PublicKey)keyStore.getKey(KeyAlias,null);
            }else{
                publicKey = createNewKeyPair().getPublic();
            }

            Signature signature = Signature.getInstance(ALGOLITHM);
            signature.initVerify(publicKey);
            signature.update(bytes);
            result = signature.verify(bytes);

        } catch (InvalidKeyException | UnrecoverableEntryException | KeyStoreException |
                NoSuchAlgorithmException | InvalidAlgorithmParameterException |
                CertificateException | SignatureException | IOException |
                NoSuchProviderException e) {
            e.printStackTrace();
        }
        return result;
    }


    private KeyPair createNewKeyPair() throws
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException {

        KeyPairGenerator keyPairGenerator;
        keyPairGenerator = KeyPairGenerator.getInstance("RSA",KeyProvider);

        keyPairGenerator.initialize(new KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .setCertificateSubject(new X500Principal(String.format("CN=%s",KeyAlias)))
                .build()
        );


        return keyPairGenerator.generateKeyPair();
    }

    X509Certificate getX509Certificate() {
        X509Certificate x509Certificate = null;
        try {
            x509Certificate = (X509Certificate) keyStore.getCertificate(KeyAlias);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        return x509Certificate;
    }
}