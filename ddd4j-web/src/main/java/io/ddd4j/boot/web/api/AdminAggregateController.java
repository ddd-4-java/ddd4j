package io.ddd4j.boot.web.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端聚合API
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminAggregateController implements AggregateController {

}