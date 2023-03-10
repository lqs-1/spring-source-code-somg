/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * Delegate(委派) for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 如果有任何的BeanDefinitionRegistryPostProcessor，则先执行invokeBeanDefinitionRegistryPostProcessors()
		// 用于存储实现了PriorityOrdered和Ordered接口的BeanDefinitionRegistryPostProcessor实例对应的实例名
		Set<String> processedBeans = new HashSet<>();
		// 若ConfigurableListableBeanFactory为BeanDefinitionRegistry类型
		// 而DefaultListableBeanFactory实现了BeanDefinitionRegistry接口，因此这边为true
		if (beanFactory instanceof BeanDefinitionRegistry) {
			// 将DefaultListableBeanFactory硬编码转型为BeanDefinitionRegistry
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// 记录BeanFactoryPostProcessor类型的处理器
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// 记录BeanDefinitionRegistryPostProcessor类型的处理器
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();
			// 遍历所有的beanFactoryPostProcessors
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					// 对于BeanDefinitionRegistryPostProcessor
					// 执行其postProcessBeanDefinitionRegistry方法
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					//记录BeanDefinitionRegistryPostProcessor类型的BeanDefinitionPostProcessor
					registryProcessors.add(registryProcessor);
				} else {
					//对于非BeanDefinitionRegistryPostProcessor类型的BeanFactoryPostProcessor，记录常规BeanFactoryPostProcessor
					regularPostProcessors.add(postProcessor);
				}
			}
			/* 配置注册的后处理器 */
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// 到目前为止，不要再这里实例化FactoryBeans，因为我们需要将regular beans非实例化，
			// 然后让BeanFactoryPostProcessor应用它们
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 将所有的BeanDefinitionRegistryPostProcessor划拨为实现PriorityOrdered、实现Ordered及其他
			// 用于保存本次要执行的BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();
			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 找出所有实现BeanDefinitionRegistryPostProcessor接口的Bean的beanName
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 校验是否实现了PriorityOrdered接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 获取ppName对应的bean实例, 添加到currentRegistryProcessors中
					// beanFactory.getBean: 这边getBean方法会触发创建ppName对应的bean对象
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			//排序，升序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到registryProcessors(用于最后执行postProcessBeanFactory方法)
			registryProcessors.addAll(currentRegistryProcessors);
			// 遍历currentRegistryProcessors, 执行postProcessBeanDefinitionRegistry方法。如果是AnnotationConfigApplicationContext方式，这里面就会有个ConfigurationClassPostProcessor，这个后处理器，会对注解配置的Bean就行解析并添加BeanDefinition
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 执行完毕清空currentRegistryProcessors,清空当前工作表中的后处理器
			currentRegistryProcessors.clear();  // 5.11 从这里开始没读

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 接着，调用实现了Ordered接口的BeanDefinitionRegistryPostProcessor
			// 因为执行完上面的BeanDefinitionRegistryPostProcessor
			// 可能会新增了其他的BeanDefinitionRegistryPostProcessor,
			// 因此需要重新查找实现BeanDefinitionRegistryPostProcessor接口的类
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);  // 从工厂中根据类型BeanDefinitionRegistryPostProcessor类获取所有的这个beanName
			for (String ppName : postProcessorNames) { // 遍历BeanDefinitionRegistryPostProcessor类型的beanName
				////如果bean的名字所对应的bean和目标类型对应(即实现了Ordered接口)
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {  // 如果processedBeans中不包含啊合格beanName且这个beanDefinitionPostProcessor是实现了Ordered接口的，就添加这个beanDefinitionPostProcessorName到processedBeans中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class)); // 上面清空了 当前的注册表处理器 （currentRegistryProcessors），因为上面获取到的BeanDefinitionPostProcessor已经被处理好了，现在添加的是可能在调用beanDefinitionPostProcessor的时候自动产生的
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory); // 排序
			registryProcessors.addAll(currentRegistryProcessors); // 添加到registryProcessors(用于最后执行postProcessBeanFactory方法)
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry); // 遍历currentRegistryProcessors, 执行postProcessBeanDefinitionRegistry方法
			currentRegistryProcessors.clear();  // 清空当前需要处理的注册处理器列表

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			//最后，遍历并调用所有的其他类型的BeanDefinitionRegistryPostProcessor
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				// 找出所有实现BeanDefinitionRegistryPostProcessor接口的类
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					// 跳过已经执行过的
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						// 设置为true再查找一次，防止出现新的BeanDefinitionRegistryPostProcessor
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);  // 对本次要执行的BeanDefinitionRegistryPostProcessor进行排序
				registryProcessors.addAll(currentRegistryProcessors); // 添加到registryProcessors(用于最后执行postProcessBeanFactory方法)
				//遍历currentRegistryProcessors, 执行postProcessBeanDefinitionRegistry方法
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();  // 清除当前要执行的BeanDefinitionRegistryPostProcessor列表
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 调用所有BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法，比如annotation方式的时候，@Configuration的类的代理对象的BeanDefinition就在里面修改
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			// 最后, 调用beanFactoryPostProcessors中的普通BeanFactoryPostProcessor的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		} else {
			// Invoke factory processors registered with the context instance.
			// 调用BeanFactoryPostProcessor实例的postProcessBeanFactory()方法处理已注册的beanFactoryPostProcessors
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 此时形参（入参）beanFactoryPostProcessors和容器中的所有BeanDefinitionRegistryPostProcessor已经全部处理完毕
		// 下面开始处理容器中的所有BeanFactoryPostProcessor
		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 找出所有实现BeanFactoryPostProcessor接口的类
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 将BeanFactoryPostProcessors划拨为实现PriorityOrdered、Ordered及其他
		// 用于记录实现了PriorityOrdered接口的BeanFactoryPostProcessor类型的处理器
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 用于记录实现了Ordered接口的BeanFactoryPostProcessor类型的处理器实例对应的实例名
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 用于记录除实现了PriorityOrdered和Ordered接口的BeanFactoryPostProcessor类型的处理器实例对应的实例名
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 对后置处理器进行分类
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
				//已经处理过的则跳过
			}
			//如BeanFactoryPostProcessor实现了PriorityOrdered接口
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			//若BeanFactoryPostProcessor实现了Ordered接口
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			} else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		//先调用实现了PriorityOrdered接口的BeanFactoryPostProcessors
		//按照priority进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 遍历priorityOrderedPostProcessors, 执行postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		//然后调用实现Ordered接口的BeanFactoryPostProcessors
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		//按照order排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 遍历orderedPostProcessors, 执行postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 最后，直接调用所有的其他无序BeanFactoryPostProcessors
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			// 获取postProcessorName对应的bean实例, 添加到nonOrderedPostProcessors, 准备执行
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		//执行postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();  // 清除元数据的缓存
	}

	/**
	 * 对配置文件中的BeanPostProcessor进行提取，并注册到beanFactory中
	 *
	 * @param beanFactory
	 * @param applicationContext
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		// 根据类型在BeanFactory里面获取beanPostProcessor的名称，这个方法是判断是否某个bean实现了BeanPostProcessor这个接口，如果是，那么就返回beanName的列表，这里获取的是BeanPostProcessor的beanName数组
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. (也就是)when
		// a bean is not eligible for(合格，够资格) getting processed by all BeanPostProcessors.
		/*
		 *BeanPostProcessorChecker是一个普通的信息打印，可能当spring配置中的后处理器
		 *（BeanPostProcessor）还没被注册就以及开始bean的初始化时便会打印出BeanPostProcessorChecker中设定的信息 ， 这里加1，是因为还要将BeanPostProcessorChecker也添加进入*/
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));  // 添加beanPostProcessor的检查器，属于哪一个BeanFactory，以及BeanPostProcessor的个数

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		//将BeanPostProcessors划拨为实现PriorityOrdered、Ordered及其他三类，是表示优先级的
		//PriorityOrdered优先级最高，Ordered其次，同为Ordered时
		//则调用Ordered接口的getOrder方法得到order值，order值越大，优先级越小
		//保存实现了PriorityOrdered接口的BeanPostProcessor，并使用PriorityOrdered保证顺序
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		//用于保存MergedBeanDefinitionPostProcessor（内置后处理器）类型的BeanPostProcessor，它们并没有在代码中被重复调用
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		//保存实现了Ordered接口的BeanPostProcessor，并使用Ordered保证顺序
		List<String> orderedPostProcessorNames = new ArrayList<>();
		//保存除实现PriorityOrdered和Ordered接口外的其他类型的BeanPostProcessor，也包括没有实现排序的
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {  // 遍历自定义的后处理器名字
			//如果BeanPostProcessor实现了PriorityOrdered接口，和匹配BeanPostProcessor的时候的步骤一样
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);  // 在getBean的时候，如果没有实例化，那么直接创建并返回
				priorityOrderedPostProcessors.add(pp);
				//且是MergedBeanDefinitionPostProcessor类型，则保存再internalPostProcessors集合中
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			//如果BeanPostProcessor实现了Ordered接口，和匹配BeanPostProcessor的时候的步骤一样
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			//BeanPostProcessor既没有实现PriorityOrdered接口也没有实现Ordered接口
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		//第一步，注册所有实现了PriorityOrdered接口的BeanPostProcessors
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);  // 对后处理器进行优先级处理
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);  // 将处理好的后处理器注册保存

		// Next, register the BeanPostProcessors that implement Ordered.
		//第二步，注册所有实现Ordered接口的BeanPostProcessors
		//用于保存实现了Ordered接口的BeanPostProcessor
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);  // 在getBean的时候，如果没有实例化，那么直接创建并返回
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		//第三步，注册所有无序的BeanPostProcessor
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		//第四步，注册所有MergedBeanDefinitionPostProcessor类型的BeanPostProcessor，并非重复注册
		//在beanFactory.addBeanPostProcessor中会先移除已经存在的BeanPostProcessor
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		//添加ApplicationListenerDetector探测器，就是检测是否已经存在这个beanPostProcessor如果存在，删除后，再添加
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}
	// 注册所有实现了PriorityOrdered接口的BeanPostProcessors
	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;  // 创建一个比较器对象
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();  // 获取beanFactory里的依赖比较器
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;  // 获取排序比较器的实例（instance）
		}
		postProcessors.sort(comparatorToUse); // 所有实现了PriorityOrdered接口和Ordered的接口的BeanPostProcessors，进行排序，列表的默认排序升序，因为数值越小优先级越高
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);  // 执行对应的对应beanDefinitionPostProcessor的postProcessBeanFactory方法
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
