QLExpress 4.x 中自定义函数主要有**注解方式**和**普通方式**两种主流写法，它比旧版本更灵活。下面我将详细介绍其核心写法、配置及实际应用。

## 一、自定义函数的两种核心写法

### 1.1 注解方式 (推荐)
通过 `@QLFunction` 注解声明，逻辑清晰且易于管理。

```java
import com.ql.util.express.annotation.QLFunction;
import org.springframework.stereotype.Component;

@Component // 确保被Spring管理
public class CustomStringFunctions {
    
    /**
     * 判断字符串是否包含子串
     * @QLFunction 注解用于声明QLExpress函数
     * name: 函数在QL表达式中使用的名称
     */
    @QLFunction(name = "contains")
    public static boolean contains(String source, String target) {
        return source != null && source.contains(target);
    }
    
    /**
     * 字符串脱敏处理
     * 保留前3位和后4位，中间用*代替
     */
    @QLFunction(name = "maskSensitive")
    public static String maskSensitive(String str) {
        if (str == null || str.length() <= 7) {
            return str;
        }
        int length = str.length();
        String prefix = str.substring(0, 3);
        String suffix = str.substring(length - 4);
        String middle = "***";
        return prefix + middle + suffix;
    }
    
    /**
     * 计算年龄
     */
    @QLFunction(name = "calculateAge")
    public static int calculateAge(Date birthday) {
        if (birthday == null) {
            return 0;
        }
        Calendar now = Calendar.getInstance();
        Calendar birth = Calendar.getInstance();
        birth.setTime(birthday);
        
        int age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
        
        // 如果当前月份小于出生月份，或者月份相同但日期小于出生日期，年龄减1
        if (now.get(Calendar.MONTH) < birth.get(Calendar.MONTH) ||
            (now.get(Calendar.MONTH) == birth.get(Calendar.MONTH) && 
             now.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))) {
            age--;
        }
        return age;
    }
}

/**
 * 数学计算函数
 */
@Component
public class CustomMathFunctions {
    
    /**
     * 四舍五入到指定小数位
     */
    @QLFunction(name = "round")
    public static double round(double value, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("小数位数不能为负数");
        }
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(scale, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    
    /**
     * 判断是否为质数
     */
    @QLFunction(name = "isPrime")
    public static boolean isPrime(int number) {
        if (number <= 1) {
            return false;
        }
        if (number <= 3) {
            return true;
        }
        if (number % 2 == 0 || number % 3 == 0) {
            return false;
        }
        for (int i = 5; i * i <= number; i += 6) {
            if (number % i == 0 || number % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }
}
```

### 1.2 普通方式
通过实现 `Function` 接口，适合需要复杂逻辑控制的场景。

```java
import com.ql.util.express.ExpressRunner;
import com.ql.util.express.InstructionSetContext;
import com.ql.util.express.instruction.op.OperatorBase;
import org.springframework.stereotype.Component;

/**
 * 复杂字符串处理函数
 */
@Component
public class ComplexStringFunction extends OperatorBase {
    
    public ComplexStringFunction(String name) {
        this.name = name;
    }
    
    @Override
    public OperateData executeInner(InstructionSetContext parent, 
                                   ArraySwap list) throws Exception {
        // 获取参数
        String source = (String) list.get(0).getObject(parent);
        String oldStr = (String) list.get(1).getObject(parent);
        String newStr = (String) list.get(2).getObject(parent);
        
        // 执行替换逻辑
        if (source == null) {
            return new OperateData(null, String.class);
        }
        String result = source.replace(oldStr, newStr);
        return new OperateData(result, String.class);
    }
}

/**
 * 日期计算函数
 */
@Component  
public class DateCalculationFunction extends OperatorBase {
    
    public DateCalculationFunction(String name) {
        this.name = name;
    }
    
    @Override
    public OperateData executeInner(InstructionSetContext parent,
                                   ArraySwap list) throws Exception {
        Date date = (Date) list.get(0).getObject(parent);
        Integer days = (Integer) list.get(1).getObject(parent);
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        
        return new OperateData(calendar.getTime(), Date.class);
    }
}
```

## 二、函数配置与注册

### 2.1 SpringBoot配置类

```java
import com.ql.util.express.ExpressRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class QLExpress4Config {
    
    @Autowired(required = false)
    private List<Object> functionBeans; // 收集所有自定义函数bean
    
    @Bean
    public ExpressRunner expressRunner() throws Exception {
        ExpressRunner runner = new ExpressRunner();
        
        // 1. 注册注解方式的函数
        registerAnnotationFunctions(runner);
        
        // 2. 注册普通方式的函数
        registerNormalFunctions(runner);
        
        // 3. 配置运行参数
        runner.setShortCircuit(true); // 开启短路计算
        runner.setPrecision(6);       // 设置计算精度
        runner.setTrace(false);       // 关闭执行跟踪
        
        return runner;
    }
    
    /**
     * 注册注解方式的自定义函数
     */
    private void registerAnnotationFunctions(ExpressRunner runner) throws Exception {
        // 方法1：通过Spring自动扫描注册
        if (functionBeans != null) {
            for (Object bean : functionBeans) {
                runner.addFunctionOfClass(bean.getClass());
            }
        }
        
        // 方法2：手动注册指定类
        runner.addFunctionOfClass(CustomStringFunctions.class);
        runner.addFunctionOfClass(CustomMathFunctions.class);
    }
    
    /**
     * 注册普通方式的自定义函数
     */
    private void registerNormalFunctions(ExpressRunner runner) throws Exception {
        // 字符串替换函数
        runner.addFunction("replace", new ComplexStringFunction("replace"));
        
        // 日期加减函数
        runner.addFunction("addDays", new DateCalculationFunction("addDays"));
        
        // 注册操作符（可作为函数使用）
        runner.addOperator("between", new BetweenOperator());
    }
    
    /**
     * 自定义操作符：判断值是否在区间内
     */
    private static class BetweenOperator extends OperatorBase {
        @Override
        public OperateData executeInner(InstructionSetContext parent, 
                                       ArraySwap list) throws Exception {
            Object value = list.get(0).getObject(parent);
            Object min = list.get(1).getObject(parent);
            Object max = list.get(2).getObject(parent);
            
            if (value instanceof Comparable) {
                Comparable cValue = (Comparable) value;
                boolean result = cValue.compareTo(min) >= 0 && cValue.compareTo(max) <= 0;
                return new OperateData(result, Boolean.class);
            }
            throw new Exception("不支持的类型比较");
        }
    }
    
    /**
     * 创建QLExpress上下文
     */
    @Bean
    public ExpressContext expressContext() {
        return new ExpressContext();
    }
}
```

### 2.2 函数管理服务

```java
import com.ql.util.express.ExpressRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class FunctionManagerService {
    
    @Autowired
    private ExpressRunner expressRunner;
    
    private final Map<String, FunctionInfo> functionRegistry = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // 注册所有函数信息，便于管理
        registerFunctionInfo();
    }
    
    /**
     * 注册函数信息
     */
    private void registerFunctionInfo() {
        // 字符串函数
        functionRegistry.put("contains", 
            new FunctionInfo("contains", "判断字符串是否包含", 
                "contains(source, target)", "String", "Boolean"));
        
        functionRegistry.put("maskSensitive", 
            new FunctionInfo("maskSensitive", "字符串脱敏处理", 
                "maskSensitive(str)", "String", "String"));
        
        // 数学函数
        functionRegistry.put("round", 
            new FunctionInfo("round", "四舍五入", 
                "round(value, scale)", "Double,Integer", "Double"));
        
        functionRegistry.put("isPrime", 
            new FunctionInfo("isPrime", "判断是否为质数", 
                "isPrime(number)", "Integer", "Boolean"));
        
        // 日期函数
        functionRegistry.put("calculateAge", 
            new FunctionInfo("calculateAge", "计算年龄", 
                "calculateAge(birthday)", "Date", "Integer"));
        
        functionRegistry.put("addDays", 
            new FunctionInfo("addDays", "日期加减", 
                "addDays(date, days)", "Date,Integer", "Date"));
    }
    
    /**
     * 获取所有函数信息
     */
    public Map<String, FunctionInfo> getAllFunctions() {
        return new HashMap<>(functionRegistry);
    }
    
    /**
     * 验证函数调用
     */
    public boolean validateFunctionCall(String functionName, int paramCount) {
        FunctionInfo info = functionRegistry.get(functionName);
        if (info == null) {
            return false;
        }
        // 这里可以添加更复杂的参数校验逻辑
        return true;
    }
    
    /**
     * 函数信息封装类
     */
    @Data
    @AllArgsConstructor
    public static class FunctionInfo {
        private String name;           // 函数名
        private String description;    // 函数描述
        private String signature;      // 函数签名
        private String paramTypes;     // 参数类型
        private String returnType;     // 返回类型
    }
}
```

## 三、实际应用示例

### 3.1 字符串处理场景

```java
/**
 * 字符串规则验证服务
 */
@Service
public class StringRuleService {
    
    @Autowired
    private ExpressRunner expressRunner;
    
    /**
     * 验证手机号格式
     */
    public boolean validatePhone(String phone) throws Exception {
        String expression = """
            // 使用自定义函数验证手机号
            if (phone == null) {
                return false;
            }
            
            // 1. 必须是11位
            if (length(phone) != 11) {
                return false;
            }
            
            // 2. 必须以1开头
            if (!startsWith(phone, "1")) {
                return false;
            }
            
            // 3. 第二位必须是3-9
            char second = phone.charAt(1);
            if (second < '3' || second > '9') {
                return false;
            }
            
            // 4. 必须全是数字
            for (i = 0; i < length(phone); i++) {
                if (!isDigit(phone.charAt(i))) {
                    return false;
                }
            }
            
            return true;
            """;
        
        Map<String, Object> context = new HashMap<>();
        context.put("phone", phone);
        
        Object result = expressRunner.execute(expression, context, null, true, false);
        return Boolean.TRUE.equals(result);
    }
    
    /**
     * 数据脱敏处理
     */
    public String processSensitiveData(String data, String dataType) throws Exception {
        String expression = """
            // 根据数据类型选择脱敏策略
            if (dataType == "PHONE") {
                // 手机号：显示前3后4
                return maskPhone(data);
            } else if (dataType == "ID_CARD") {
                // 身份证：显示前6后4
                return maskIdCard(data);
            } else if (dataType == "BANK_CARD") {
                // 银行卡：显示前6后4
                return maskBankCard(data);
            } else {
                // 默认脱敏
                return maskSensitive(data);
            }
            """;
        
        Map<String, Object> context = new HashMap<>();
        context.put("data", data);
        context.put("dataType", dataType);
        
        Object result = expressRunner.execute(expression, context, null, true, false);
        return result.toString();
    }
    
    /**
     * 自定义手机号脱敏函数
     */
    @QLFunction(name = "maskPhone")
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
    
    /**
     * 自定义身份证脱敏函数
     */
    @QLFunction(name = "maskIdCard")
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + 
               idCard.substring(idCard.length() - 4);
    }
}
```

### 3.2 数据验证场景

```java
/**
 * 复杂数据验证服务
 */
@Service
public class DataValidationService {
    
    @Autowired
    private ExpressRunner expressRunner;
    
    /**
     * 验证用户注册信息
     */
    public ValidationResult validateUserRegistration(User user) throws Exception {
        String expression = """
            // 定义验证结果
            errors = [];
            warnings = [];
            
            // 1. 用户名验证
            if (username == null || length(username) < 4) {
                errors.add("用户名至少4位");
            } else if (contains(username, "admin")) {
                warnings.add("用户名包含敏感词");
            }
            
            // 2. 密码强度验证
            if (password == null || length(password) < 8) {
                errors.add("密码至少8位");
            } else {
                hasUpper = false;
                hasLower = false;
                hasDigit = false;
                hasSpecial = false;
                
                for (i = 0; i < length(password); i++) {
                    ch = password.charAt(i);
                    if (isUpperCase(ch)) hasUpper = true;
                    else if (isLowerCase(ch)) hasLower = true;
                    else if (isDigit(ch)) hasDigit = true;
                    else hasSpecial = true;
                }
                
                if (!hasUpper) warnings.add("密码应包含大写字母");
                if (!hasLower) warnings.add("密码应包含小写字母");
                if (!hasDigit) warnings.add("密码应包含数字");
                if (!hasSpecial) warnings.add("密码应包含特殊字符");
            }
            
            // 3. 邮箱验证
            if (email != null) {
                if (!contains(email, "@")) {
                    errors.add("邮箱格式不正确");
                }
            }
            
            // 4. 年龄验证
            if (birthday != null) {
                age = calculateAge(birthday);
                if (age < 18) {
                    errors.add("年龄未满18岁");
                } else if (age > 100) {
                    warnings.add("年龄异常，请确认");
                }
            }
            
            // 返回验证结果
            return map(
                "valid", size(errors) == 0,
                "errors", errors,
                "warnings", warnings
            );
            """;
        
        Map<String, Object> context = new HashMap<>();
        context.put("username", user.getUsername());
        context.put("password", user.getPassword());
        context.put("email", user.getEmail());
        context.put("birthday", user.getBirthday());
        
        // 执行验证
        Object result = expressRunner.execute(expression, context, null, true, false);
        
        // 处理验证结果
        return parseValidationResult(result);
    }
    
    /**
     * 自定义字符类型判断函数
     */
    @QLFunction(name = "isUpperCase")
    public static boolean isUpperCase(char ch) {
        return Character.isUpperCase(ch);
    }
    
    @QLFunction(name = "isLowerCase")
    public static boolean isLowerCase(char ch) {
        return Character.isLowerCase(ch);
    }
    
    @QLFunction(name = "isDigit")
    public static boolean isDigit(char ch) {
        return Character.isDigit(ch);
    }
}
```

### 3.3 业务计算场景

```java
/**
 * 业务规则计算服务
 */
@Service
public class BusinessCalculationService {
    
    @Autowired
    private ExpressRunner expressRunner;
    
    /**
     * 计算订单价格
     */
    public OrderPrice calculateOrderPrice(Order order, Customer customer) throws Exception {
        String expression = """
            // 基础价格
            basePrice = order.amount * order.unitPrice;
            
            // 1. 数量折扣
            quantityDiscount = 0;
            if (order.quantity >= 100) {
                quantityDiscount = 0.1;  // 10%折扣
            } else if (order.quantity >= 50) {
                quantityDiscount = 0.05; // 5%折扣
            }
            
            // 2. 客户等级折扣
            levelDiscount = 0;
            if (customer.level == "VIP") {
                levelDiscount = 0.15;
            } else if (customer.level == "GOLD") {
                levelDiscount = 0.1;
            } else if (customer.level == "SILVER") {
                levelDiscount = 0.05;
            }
            
            // 3. 促销折扣（使用自定义函数计算）
            promotionDiscount = calculatePromotionDiscount(
                order.productId, 
                order.quantity, 
                order.orderDate
            );
            
            // 4. 计算最终折扣（取最大值）
            totalDiscount = max(quantityDiscount, levelDiscount, promotionDiscount);
            
            // 5. 折扣金额
            discountAmount = round(basePrice * totalDiscount, 2);
            
            // 6. 最终价格
            finalPrice = basePrice - discountAmount;
            
            // 7. 运费计算
            shippingCost = calculateShipping(
                order.weight, 
                customer.address.city, 
                order.urgent
            );
            
            // 8. 总价
            totalPrice = finalPrice + shippingCost;
            
            // 返回计算结果
            return map(
                "basePrice", basePrice,
                "quantityDiscount", quantityDiscount,
                "levelDiscount", levelDiscount,
                "promotionDiscount", promotionDiscount,
                "totalDiscount", totalDiscount,
                "discountAmount", discountAmount,
                "finalPrice", finalPrice,
                "shippingCost", shippingCost,
                "totalPrice", totalPrice
            );
            """;
        
        Map<String, Object> context = new HashMap<>();
        context.put("order", order);
        context.put("customer", customer);
        
        Object result = expressRunner.execute(expression, context, null, true, false);
        return mapToOrderPrice(result);
    }
    
    /**
     * 自定义促销折扣计算函数
     */
    @QLFunction(name = "calculatePromotionDiscount")
    public static double calculatePromotionDiscount(String productId, 
                                                   int quantity, 
                                                   Date orderDate) {
        // 这里可以实现复杂的促销逻辑
        double discount = 0.0;
        
        // 示例逻辑：特定产品促销
        if ("SPECIAL_001".equals(productId)) {
            discount = 0.2; // 20%折扣
        }
        
        // 节假日促销
        Calendar cal = Calendar.getInstance();
        cal.setTime(orderDate);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        
        if (month == 11 && day == 11) { // 双11
            discount = Math.max(discount, 0.3);
        }
        
        return discount;
    }
    
    /**
     * 自定义运费计算函数
     */
    @QLFunction(name = "calculateShipping")
    public static double calculateShipping(double weight, String city, boolean urgent) {
        double baseCost = 0.0;
        
        // 根据重量计算基础运费
        if (weight <= 1.0) {
            baseCost = 10.0;
        } else if (weight <= 5.0) {
            baseCost = 20.0;
        } else {
            baseCost = 20.0 + (weight - 5.0) * 5.0;
        }
        
        // 偏远地区附加费
        if (isRemoteArea(city)) {
            baseCost += 15.0;
        }
        
        // 加急费用
        if (urgent) {
            baseCost *= 1.5;
        }
        
        return baseCost;
    }
    
    /**
     * 自定义最大值函数
     */
    @QLFunction(name = "max")
    public static double max(double... values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double maxValue = values[0];
        for (double value : values) {
            if (value > maxValue) {
                maxValue = value;
            }
        }
        return maxValue;
    }
}
```

## 四、最佳实践建议

1. **函数设计原则**
    - 保持函数功能单一，每个函数只做一件事
    - 函数命名要有意义，使用驼峰命名法
    - 提供必要的参数验证和错误处理

2. **性能优化**
   ```java
   // 使用缓存提高重复执行性能
   @Service
   public class OptimizedRuleService {
       private final LRUCache<String, Object> expressionCache = 
           new LRUCache<>(1000);
       
       public Object executeWithCache(String expression, 
                                     Map<String, Object> context) {
           String cacheKey = generateCacheKey(expression, context);
           Object cachedResult = expressionCache.get(cacheKey);
           if (cachedResult != null) {
               return cachedResult;
           }
           
           Object result = expressRunner.execute(expression, context, 
                                               null, true, false);
           expressionCache.put(cacheKey, result);
           return result;
       }
   }
   ```

3. **安全性考虑**
    - 避免在函数中执行危险操作（如文件删除、系统命令）
    - 对用户输入的表达式进行严格验证
    - 使用沙箱模式运行不可信表达式

通过以上方式，你可以根据具体业务需求灵活创建和使用自定义函数，充分发挥QLExpress4在动态规则处理方面的优势。