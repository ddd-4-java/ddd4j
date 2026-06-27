package io.ddd4j.core;

import hitool.core.lang3.RandomString;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RandomString_Test {

    static RandomString random = new RandomString(8);

    public static void main(String[] args) throws Exception {
        System.out.println(random.nextString());
        for (int i = 0; i < 50; i++) {
            System.out.println(random.nextNumberString());
        }
    }


}
