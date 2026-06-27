package io.hiwepy.cloud.base.core;

import com.github.hiwepy.jwt.JwtPayload;
import com.github.hiwepy.jwt.token.SignedWithSecretKeyJWTRepository;
import com.github.hiwepy.jwt.utils.SecretKeyUtils;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Token_Test {

    private static String seed = "xx1WET12^%3^(WE45";
    private static String sKey = "xx288&^^%$!@#4dc";
    private static String ivParameter = "0123456789abcdef";

    private static String rsaPubKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1CU7En9Xtl6/nnJ4tTjz6U08wFy5vMBA1UdGA5m22FhRt4B0exvhYBE1XyZp0jm4MQ071t5Re64VN8Gsx1AGujx0PW7leOSQmw1Teb3pgucT7ENn5bVmG52pe3//aWhgZUYe+8epYOK89PhmqwD7t80FZKp+T10QDeQizWx6ZEQZMApzlTj/GX64FC2Qld42B/Dcg+crjX0cI+3f9YIwQmYR9/6DifxybAs37uHSuzneqVc9Q6jJ/glYqsOuikxrV1eOgW8/gO+b6OVVKFRxgjUE4GEolkP6DdCKD/+9nrtITtHTCgka1LcDOo0PEAlu8B9UCgtB44HiQ6OTKo074QIDAQAB";
    private static String rsaPriKey = "v+PYuxl8cN+hsEsp4Z6vSTGakStYdwiABjhi9GeMFCfwcJS4nBNJxsOE4OW9AUzzJeFVD8WDt+9VNcBXSkcQPZygZXNpcnKX1/YfDOoKdz/uj1omrBMdVKw3LRR5US7wqS9lBkyFqQ9upQPW5nYvxUvD4wo+ZrSwKXscsRqmTDEF7ZZzs/fRt1sH21n25GAc9RUtheDzJtx8T55+1CRSz9nq4v2x6tp9n15Vi+9Pt/gmXQy0dd5LV9okIKZisz9aQ53dEQ9+PO0wSKDV2vfkZDL6ZH1hlfpkFKsSJDapG153yy8TzFm93+jlntaPw1VBSO8NQZSN0DiB2TzNPtadr2uHoFHOwafiBaSqqVlxQ8NRnrP1KIb47KF2pTdIv52EAc44ucrfp6JzfNNzxp8AX6f0lvoV0Sl8fbGqqVEJYf+wR235SwRc5GtnMwWaWC99cO4cHFwdR3SZAksVOJSuoB1bEr9Ssgqz6+h4iCl92PD3L2np4WUdKmFM9GjXvnscOUNVWwDspA8uDoXgJdTgIap+6wbBNS1jeKplJffJe7Bne6eM39bb4dIjZZj9gc0FWeBPq48DuNhFBPlCPz+hFcFLqfjaxTBa1mmWCYcRyITZtLG26F3qtUX7WoSFKijhdn4EjLtH8SxwYquJMB/vtJWEr1HgVdocwFVFoFocI5bqQl4LcXh6RRkAqjcyDt2h6qhbeprzyuhY5WCefwIGL412EqOMZFx0AA+LDUNGSU62+ucUgHhR6VX7lDGLPd2DWPsP6Kso7OmNhTT7BygYEHf93zXsvwHpYZcsNZL5gviEbbVI4IALDUixe8lBpgytqSIGOAaxj9XVd0hYK6g0xwZaA/Sjq27SI0tahuG1JDXIoT/nSYh53mH9ysnigAnnvazWq1qnwKY5G+o62r0CF9i4RmJqMK7wljsv77oPckOXjNjyCvW+4Zx4DVwZSs748dBsqSX/ntAOnWZksFUjbe0XDHSAw/6tpHlNBXdxrqtU7Zul91cZSXK7KCdtjbnqbZaMcudnN8SEY3qtyF2kHr9Lkp7Q/KlbSUfm3ub/j9G9uNGR3qfZbydVYKO/tgho8rgdBye3oTxuSTv+tk9HI/3Gcw4V7/RHEHYKutrG1DS0vvQe4AsZn7OczrYRewLhTLZgnMW0zrOS1HFLhmxLIUNFlnqLw9fBUrcaZS4QQH7g6CKpx8njU2SVfUgUp5Kixd9f2MrlAvLaA2MI0LZz4Kq00cGIsRnwUoJPVWDLOIAtme/MC5hJ8mDOfnfAIIdt+rURkVsEkga74ddI4F+J2AeMYs3mrRezW8+lNA7VLvumwZpeldrWOotha/EPMYlA4s+AxY1FPOM1MN+KlWvlLcoLf6H+VXS5K1TlbxDlYRKv2t0iim7fzMikC953pnqPUmG2Y8ni0Y0nFUIPlrJ81SXe5X2qnBheVExw2/K4NkMpThCw7sw5cAI/CdOYewL7GLeEYqAHGRjiYy2T2hwehMRP2C+M5qs3WXkaOuXIsDWukCokpy33dY/cjJ/F5zF9Dz77h3OTto6qxwdLz+L7/8eYW5DqNLaTJGdoPCh9HwI3FK9EJntgOVxhtlV2ME0+VgNnzOtpGI5Xdj9/YmFIo4W4llNikeUF0XuRTaLDEeI=";

    public static void main(String[] args) throws Exception {

        AesBytesEncryptor aesBytesEncryptor = new AesBytesEncryptor(sKey, ivParameter);

        System.out.println("公钥 - Base64: " + rsaPubKey);
        byte[] publicKeyBytes = Base64.getDecoder().decode(rsaPubKey);
        PublicKey publicKey = SecretKeyUtils.genPublicKey(SecretKeyUtils.KEY_RSA, publicKeyBytes);
        System.out.println("公钥: " + publicKey);

        // 2、从配置文件中获取私钥
        byte[] privateKeyBytes = Base64.getDecoder().decode(rsaPriKey);
        String pri_key = Base64.getEncoder().encodeToString(aesBytesEncryptor.decrypt(privateKeyBytes));
        System.out.println("私钥 - Base64: " + pri_key );
        PrivateKey privateKey = SecretKeyUtils.genPrivateKey(SecretKeyUtils.KEY_RSA, aesBytesEncryptor.decrypt(privateKeyBytes));
        System.out.println("私钥: " + privateKey);

        SignedWithSecretKeyJWTRepository secretKeyJWTRepository = new SignedWithSecretKeyJWTRepository();

        String jwtId = "1000";
        String subject = "admin";
        String issuer = "www.hiwepy.com";
        String audience = "2000";
        String roles = "admin";
        String permissions = "*";
        String algorithm = "RS256";
        long period = 1000 * 60 * 60 * 24 * 7;

        String token = secretKeyJWTRepository.issueJwt(privateKey, jwtId, subject, issuer, audience, roles, permissions, algorithm, period);
        System.out.println("Token: " + token);

        boolean isVerification = secretKeyJWTRepository.verify(publicKey, token, false);
        System.out.println("Token Verification: " + isVerification);

        JwtPayload payload = secretKeyJWTRepository.getPlayload(publicKey, token,false);
        System.out.println("Token Payload: " + payload);

    }

}
