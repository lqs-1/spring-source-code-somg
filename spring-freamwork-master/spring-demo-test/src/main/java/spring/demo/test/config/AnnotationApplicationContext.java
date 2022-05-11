package spring.demo.test.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author : 李奇凇
 * @date : 2022/5/10 22:09
 * @do :
 */


@Configuration
@ComponentScan("spring.demo.test")
@EnableAspectJAutoProxy
public class AnnotationApplicationContext {
}
