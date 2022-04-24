package spring.demo.test.beanTest;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmlBeanTest {
	public static void main(String[] args) {
		// xml配置文件方式
		ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("XmlBeanApplication.xml");
		String[] beanDefinitionNames = classPathXmlApplicationContext.getBeanDefinitionNames();
		for (String beanDefinitionName : beanDefinitionNames) {
			System.out.println(beanDefinitionName);
		}
	}
}
