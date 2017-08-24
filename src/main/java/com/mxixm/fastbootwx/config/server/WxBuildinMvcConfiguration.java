package com.mxixm.fastbootwx.config.server;

import com.mxixm.fastbootwx.annotation.EnableWxMvc;
import com.mxixm.fastbootwx.config.invoker.WxVerifyProperties;
import com.mxixm.fastbootwx.controller.WxVerifyController;
import com.mxixm.fastbootwx.controller.invoker.WxApiInvokeSpi;
import com.mxixm.fastbootwx.controller.invoker.common.WxMediaResourceMessageConverter;
import com.mxixm.fastbootwx.module.menu.WxMenuManager;
import com.mxixm.fastbootwx.module.message.WxMessageProcesser;
import com.mxixm.fastbootwx.module.message.support.WxAsyncMessageReturnValueHandler;
import com.mxixm.fastbootwx.mvc.advice.WxMediaResponseBodyAdvice;
import com.mxixm.fastbootwx.mvc.advice.WxMessageResponseBodyAdvice;
import com.mxixm.fastbootwx.mvc.advice.WxStringResponseBodyAdvice;
import com.mxixm.fastbootwx.mvc.annotation.WxMappingHandlerMapping;
import com.mxixm.fastbootwx.mvc.param.WxArgumentResolver;
import com.mxixm.fastbootwx.support.WxUserProvider;
import com.mxixm.fastbootwx.web.WxOAuth2Interceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(WxMvcProperties.class)
public class WxBuildinMvcConfiguration implements ImportAware {

    private static final Log logger = LogFactory.getLog(MethodHandles.lookup().lookupClass());

    private final WxVerifyProperties wxVerifyProperties;

    private final BeanFactory beanFactory;

    private final WxMessageProcesser wxMessageProcesser;

    private final WxApiInvokeSpi wxApiInvokeSpi;

    private final WxMvcProperties wxMvcProperties;

    private boolean menuAutoCreate = true;

    public WxBuildinMvcConfiguration(WxMvcProperties wxMvcProperties, WxVerifyProperties wxVerifyProperties, BeanFactory beanFactory, @Lazy WxMessageProcesser wxMessageProcesser, @Lazy WxApiInvokeSpi wxApiInvokeSpi) {
        this.wxMvcProperties = wxMvcProperties;
        this.wxVerifyProperties = wxVerifyProperties;
        this.beanFactory = beanFactory;
        this.wxMessageProcesser = wxMessageProcesser;
        this.wxApiInvokeSpi = wxApiInvokeSpi;
    }

    @Bean
    public WxVerifyController wxVerifyController() {
        return new WxVerifyController(wxVerifyProperties);
    }

    @Bean
    public WxMappingHandlerMapping wxRequestMappingHandlerMapping() {
        WxMappingHandlerMapping wxMappingHandlerMapping = new WxMappingHandlerMapping(wxVerifyController(), wxMenuManager());
        wxMappingHandlerMapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return wxMappingHandlerMapping;
    }

    @Bean
    public WxMenuManager wxMenuManager() {
        return new WxMenuManager(wxApiInvokeSpi, menuAutoCreate);
    }

    @Bean
    public WxMediaResourceMessageConverter wxMediaResourceMessageConverter() {
        return new WxMediaResourceMessageConverter();
    }

    @Bean
    public WxMvcConfigurer wxMvcConfigurer() {
        return new WxMvcConfigurer(wxOAuth2Interceptor(), wxMvcProperties);
    }

    @Bean
    public WxMvcAdapterCustomer wxMvcAdapterCustomer() {
        return new WxMvcAdapterCustomer();
    }

    @Bean
    public WxMessageResponseBodyAdvice wxMessageResponseBodyAdvice() {
        return new WxMessageResponseBodyAdvice(wxMessageProcesser);
    }

    @Bean
    public WxStringResponseBodyAdvice wxStringResponseBodyAdvice() {
        return new WxStringResponseBodyAdvice(wxMessageProcesser);
    }

    @Bean
    public WxMediaResponseBodyAdvice wxMediaResponseBodyAdvice() {
        return new WxMediaResponseBodyAdvice();
    }

    @Bean
    public WxOAuth2Interceptor wxOAuth2Interceptor() {
        return new WxOAuth2Interceptor();
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        AnnotationAttributes annotationAttributes = AnnotationAttributes.fromMap(
                importMetadata.getAnnotationAttributes(EnableWxMvc.class.getName(), false));
        this.menuAutoCreate = annotationAttributes.getBoolean("menuAutoCreate");
    }

    /**
     * 本来想用WxMvcConfigurer，但是因为那个配置不能修改returnValueHandlers和argumentResolvers的顺序
     * 所以用了这个
     */
    public static class WxMvcAdapterCustomer implements InitializingBean, BeanFactoryAware {

        private BeanFactory beanFactory;

        @Override
        public void afterPropertiesSet() throws Exception {
            RequestMappingHandlerAdapter requestMappingHandlerAdapter = this.beanFactory.getBean(RequestMappingHandlerAdapter.class);
            List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();
            List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList<>();
            if (beanFactory instanceof ConfigurableBeanFactory) {
                argumentResolvers.add(new WxArgumentResolver((ConfigurableBeanFactory) beanFactory));
            } else {
                argumentResolvers.add(new WxArgumentResolver(beanFactory.getBean(WxUserProvider.class)));
            }
            returnValueHandlers.add(beanFactory.getBean(WxAsyncMessageReturnValueHandler.class));
            argumentResolvers.addAll(requestMappingHandlerAdapter.getArgumentResolvers());
            returnValueHandlers.addAll(requestMappingHandlerAdapter.getReturnValueHandlers());
            requestMappingHandlerAdapter.setArgumentResolvers(argumentResolvers);
            requestMappingHandlerAdapter.setReturnValueHandlers(returnValueHandlers);
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory;
        }
    }

    public static class WxMvcConfigurer extends WebMvcConfigurerAdapter {

        private HandlerInterceptor wxOAuth2Interceptor;

        private WxMvcProperties wxMvcProperties;

        public WxMvcConfigurer(HandlerInterceptor wxOAuth2Interceptor, WxMvcProperties wxMvcProperties) {
            this.wxOAuth2Interceptor = wxOAuth2Interceptor;
            this.wxMvcProperties = wxMvcProperties;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(wxOAuth2Interceptor)
                    .addPathPatterns(wxMvcProperties.getIncludePatterns().toArray(new String[wxMvcProperties.getIncludePatterns().size()]))
                    .excludePathPatterns(wxMvcProperties.getIncludePatterns().toArray(new String[wxMvcProperties.getIncludePatterns().size()]));
        }

    }

    /*public static class WxMvcConfigurer extends WebMvcConfigurerAdapter {

        private WxArgumentResolver wxArgumentResolver;

        private WxAsyncMessageReturnValueHandler wxAsyncMessageReturnValueHandler;

        /**
         * 之前这里产生循环依赖，因为ConversionService是这个里面生成的，而conversionService又被WxApiExecutor依赖
         * WxApiExecutor -> WxApiInvokeSpi -> WxUserProvider -> WxMvcConfigurer -> ConversionService -> WxAPIExecutor
         * 于是产生了循环依赖
         * 临时处理先把ConversionService的依赖去掉，后期考虑优化依赖关系
         *
         * @param wxUserProvider
         * @param beanFactory
         *
        public WxMvcConfigurer(WxUserProvider wxUserProvider, BeanFactory beanFactory) {
            if (beanFactory instanceof ConfigurableBeanFactory) {
                this.wxArgumentResolver = new WxArgumentResolver((ConfigurableBeanFactory) beanFactory);
            } else {
                this.wxArgumentResolver = new WxArgumentResolver(wxUserProvider);
            }
            this.wxAsyncMessageReturnValueHandler = beanFactory.getBean(WxAsyncMessageReturnValueHandler.class);
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
            argumentResolvers.add(this.wxArgumentResolver);
        }

        @Override
        public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
            returnValueHandlers.add(0, wxAsyncMessageReturnValueHandler);
        }

        /**
         * WebMvcConfigurationSupport添加的MessageConverter不会被添加到SpringBoot全局的HttpMessageConverters中
         */
        /*@Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
			converters.add(new WxMediaResourceMessageConverter());
		}*
    }
    */
}