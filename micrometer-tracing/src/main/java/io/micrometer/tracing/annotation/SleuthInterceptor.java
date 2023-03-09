package io.micrometer.tracing.annotation;

/**
 * Interceptor that creates or continues a span depending on the provided annotation. Also
 * it adds logs and tags if necessary.
 *
 * @author Marcin Grzejszczak
 */
class SleuthInterceptor implements IntroductionInterceptor, BeanFactoryAware {

	private BeanFactory beanFactory;

	private SleuthMethodInvocationProcessor methodInvocationProcessor;

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Method method = invocation.getMethod();
		if (method == null) {
			return invocation.proceed();
		}
		Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method, invocation.getThis().getClass());
		NewSpan newSpan = SleuthAnnotationUtils.findAnnotation(mostSpecificMethod, NewSpan.class);
		ContinueSpan continueSpan = SleuthAnnotationUtils.findAnnotation(mostSpecificMethod, ContinueSpan.class);
		if (newSpan == null && continueSpan == null) {
			return invocation.proceed();
		}
		return methodInvocationProcessor().process(invocation, newSpan, continueSpan);
	}

	private SleuthMethodInvocationProcessor methodInvocationProcessor() {
		if (this.methodInvocationProcessor == null) {
			this.methodInvocationProcessor = this.beanFactory.getBean(SleuthMethodInvocationProcessor.class);
		}
		return this.methodInvocationProcessor;
	}

	@Override
	public boolean implementsInterface(Class<?> intf) {
		return true;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

}
