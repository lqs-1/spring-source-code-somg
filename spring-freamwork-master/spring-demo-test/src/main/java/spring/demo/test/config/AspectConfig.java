package spring.demo.test.config;


import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import spring.demo.test.service.UserService;
import spring.demo.test.service.impl.UserServiceImpl;


/**
 * @author : 李奇凇
 * @date : 2022/5/7 11:00
 * @do : 切面类
 */


@Aspect
@Service
public class AspectConfig {

	//此处定义一个通用的切点,以便下方4个通知使用
	@Pointcut("execution(* spring.demo.test.service.impl.*.*(..))")
	public void logAop() {
	}

	// 环绕方式，方法执行前后，proceed是放行，放行执行完了直接环绕后一个
	@Around(value = "logAop()")
	public Object around(ProceedingJoinPoint point) throws Throwable {
		long startTime = System.currentTimeMillis();
		Object result = point.proceed();
		long endTime = System.currentTimeMillis();
		System.out.println(endTime - startTime);
		return result;
	}

//	// 方法调用之前执行，可以拿到目标对象进行修改
//	@Before(value = "logAop()")
//	public void before(JoinPoint joinPoint){
//		UserService result = (UserService) joinPoint.getTarget();
//		System.out.println(result.ToString());
//		if (result instanceof UserService){
//
//			System.out.println(result);
//		}else{
//
//			System.out.println("before");
//		}
//	}


	// 方法调用之后执行，可以拿到目标对象进行修改，但是没啥意义
//	@After(value = "logAop()")
//	public void after(JoinPoint joinPoint){
//		UserService result = (UserService) joinPoint.getTarget();
//		System.out.println(result.ToString());
//		if (result instanceof UserService){
//
//			System.out.println(result);
//		}else{
//
//			System.out.println("before");
//		}
//	}



}
