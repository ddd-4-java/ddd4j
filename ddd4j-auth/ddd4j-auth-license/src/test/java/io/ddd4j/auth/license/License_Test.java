package io.ddd4j.auth.license;

import io.ddd4j.auth.license.creator.LicenseCreator;
import io.ddd4j.auth.license.creator.LicenseCreatorParam;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class License_Test {

    private LicenseVerify licenseVerify;

    public void setLicenseVerify(LicenseVerify licenseVerify) {
        this.licenseVerify = licenseVerify;
    }

    @Test
    public void licenseVerify() {
        System.out.println("license是否有效：" + licenseVerify.verify());
    }

    @Test
    public void licenseCreate() {
        // 生成license需要的一些参数
        LicenseCreatorParam param = new LicenseCreatorParam();
        param.setSubject("ioserver");
        param.setPrivateAlias("privateKey");
        param.setKeyPass("a123456");
        param.setStorePass("a123456");
        param.setLicensePath("D:\\licenseTest\\license.lic");
        param.setPrivateKeysStorePath("D:\\licenseTest\\privateKeys.keystore");
        Calendar issueCalendar = Calendar.getInstance();
        param.setIssuedTime(issueCalendar.getTime());
        Calendar expiryCalendar = Calendar.getInstance();
        expiryCalendar.set(2020, Calendar.DECEMBER, 31, 23, 59, 59);
        param.setExpiryTime(expiryCalendar.getTime());
        param.setConsumerType("user");
        param.setConsumerAmount(1);
        param.setDescription("测试");
        LicenseCreator licenseCreator = new LicenseCreator(param);
        // 生成license
        licenseCreator.generateLicense();
    }


}
