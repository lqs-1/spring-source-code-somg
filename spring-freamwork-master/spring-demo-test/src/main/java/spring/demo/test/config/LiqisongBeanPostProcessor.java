package spring.demo.test.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * @author : 李奇凇
 * @date : 2022/5/10 8:40
 * @do :
 */
//@Component
public class LiqisongBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("beforeBeanPostProcessor:" + beanName);
		return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("afterBeanPostProcessor:" + beanName);
		return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
	}
}
