package io.hiwepy.cloud.base.core;

import com.github.hiwepy.jwt.utils.SecretKeyUtils;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class KeyPair_Test {

    private static String seed = "xx1WET12^%3^(WE45";
    private static String sKey = "xx288&^^%$!@#4dc";
    private static String ivParameter = "0123456789abcdef";

    public static void main(String[] args) throws Exception {

        KeyPair keyPair = SecretKeyUtils.genKeyPair(seed, SecretKeyUtils.KEY_RSA, 2048);

        PublicKey pubKey = keyPair.getPublic();
        byte[] publicKeyBytes = pubKey.getEncoded();
        String pub_key = Base64.getEncoder().encodeToString(publicKeyBytes);
        System.out.println("公钥 - Base64: " + pub_key);

        PrivateKey priKey = keyPair.getPrivate();
        byte[] privateKeyBytes = priKey.getEncoded();
        String pri_key = Base64.getEncoder().encodeToString(privateKeyBytes);
        System.out.println("私钥 - Base64: " + pri_key);

        //AesBytesEncryptor aesBytesEncryptor = new AesBytesEncryptor("xx288&^^%$!@#4dc", "0123456789abcdef");
        AesBytesEncryptor aesBytesEncryptor = new AesBytesEncryptor(sKey, ivParameter);
        String rsaPriKey = Base64.getEncoder().encodeToString(aesBytesEncryptor.encrypt(privateKeyBytes));
        System.out.println("私钥 - Base64 + AES: " + rsaPriKey);

        privateKeyBytes = Base64.getDecoder().decode(rsaPriKey);
        pri_key = Base64.getEncoder().encodeToString(aesBytesEncryptor.decrypt(privateKeyBytes));
        System.out.println("私钥 - Base64: " + pri_key );

        PublicKey publicKey = SecretKeyUtils.genPublicKey(SecretKeyUtils.KEY_RSA, publicKeyBytes);
        PrivateKey privateKey = SecretKeyUtils.genPrivateKey(SecretKeyUtils.KEY_RSA, aesBytesEncryptor.decrypt(privateKeyBytes));
        System.out.println("公钥: " + publicKey);
        System.out.println("私钥: " + privateKey);

    }

}
