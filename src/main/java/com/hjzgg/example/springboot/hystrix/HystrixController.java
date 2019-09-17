package com.hjzgg.example.springboot.hystrix;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.netflix.hystrix.*;
import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import com.netflix.hystrix.serial.SerialHystrixDashboardData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author hujunzheng
 * @create 2019-09-16 14:57
 **/
@RestController
@RequestMapping("/hystrix")
public class HystrixController {

    @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public String metrics() {
        JSONArray metrics = new JSONArray();
        SerialHystrixDashboardData.toMultipleJsonStrings(
                new HystrixDashboardStream.DashboardData(
                        HystrixCommandMetrics.getInstances(),
                        HystrixThreadPoolMetrics.getInstances(),
                        HystrixCollapserMetrics.getInstances()
                )
        ).forEach(metric -> metrics.add(JSON.parse(metric)));
        return metrics.toJSONString();
    }

    @GetMapping(value = "/test", produces = MediaType.TEXT_PLAIN_VALUE)
    public String test(
            @Autowired HttpServletRequest request
            , @RequestParam String groupKey
            , @RequestParam String commandKey
            , @RequestParam String poolKey
            , @RequestParam Integer index
    ) {

        String result = new ThreadPoolHystrixCommand(groupKey, commandKey, poolKey, index).setRequest(request).execute();
        return String.format("%s --> %s_%s_%s_%s", index, groupKey, commandKey, poolKey, result);
    }

    @GetMapping(value = "/test2", produces = MediaType.TEXT_PLAIN_VALUE)
    public String test2(
            @RequestParam String groupKey
            , @RequestParam String commandKey
            , @RequestParam Integer index
    ) {

        String result = new SemaphoreHystrixCommand(groupKey, commandKey, index).execute();
        return String.format("%s --> %s_%s_%s", index, groupKey, commandKey, result);
    }

    private static class SemaphoreHystrixCommand extends HystrixCommand<String> {

        private String groupKey;
        private String commandKey;
        private String poolKey;
        private Integer index;

        public SemaphoreHystrixCommand(String groupKey, String commandKey, Integer index) {
            super(
                    HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                            .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
                            .andCommandPropertiesDefaults(    // 配置熔断器
                                    HystrixCommandProperties.Setter()
                                            //熔断器在整个统计时间内是否开启的阀值
                                            .withCircuitBreakerEnabled(true)
                                            //至少有3个请求才进行熔断错误比率计算(10s中内最少的请求量，大于该值，断路器配置才会生效)
                                            .withCircuitBreakerRequestVolumeThreshold(3)
                                            //当出错率超过10%后熔断器启动
                                            .withCircuitBreakerErrorThresholdPercentage(10)
                                            //统计滚动的时间窗口
                                            .withMetricsRollingStatisticalWindowInMilliseconds(5000)
                                            //熔断器工作时间，超过这个时间，先放一个请求进去，成功的话就关闭熔断，失败就再等一段时间
                                            .withCircuitBreakerSleepWindowInMilliseconds(2000)
                                            //配置信号量隔离
                                            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                                            // execution(执行)调用最大的并发数
                                            .withExecutionIsolationSemaphoreMaxConcurrentRequests(3)
                                            //fallback(降级)调用最大的并发数
                                            .withFallbackIsolationSemaphoreMaxConcurrentRequests(10)
                            )
            );
            this.groupKey = groupKey;
            this.commandKey = commandKey;
            this.index = index;
        }

        @Override
        protected String run() throws Exception {
            if (this.index < 20) {
                System.out.println("异常...");
                throw new Exception("异常...");
            }
            return "NORMAL - " + Thread.currentThread().getName();
        }

        @Override
        protected String getFallback() {
            return "FALLBACK - " + Thread.currentThread().getName();
        }
    }

    private static class ThreadPoolHystrixCommand extends HystrixCommand<String> {
        private String groupKey;
        private String commandKey;
        private String poolKey;
        private Integer index;

        private HttpServletRequest request;

        public ThreadPoolHystrixCommand(String groupKey, String commandKey, String poolKey, Integer index) {
            super(
                    HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                            .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
                            .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(poolKey))
                            //配置线程池
                            .andThreadPoolPropertiesDefaults(
                                    HystrixThreadPoolProperties.Setter()
                                            // 配置线程池里的线程数，设置足够多线程，以防未熔断却打满threadpool
                                            .withCoreSize(10)
                            )
                            .andCommandPropertiesDefaults(    // 配置熔断器
                                    HystrixCommandProperties.Setter()
                                            //熔断器在整个统计时间内是否开启的阀值
                                            .withCircuitBreakerEnabled(true)
                                            //至少有3个请求才进行熔断错误比率计算(10s中内最少的请求量，大于该值，断路器配置才会生效)
                                            .withCircuitBreakerRequestVolumeThreshold(3)
                                            //当出错率超过10%后熔断器启动
                                            .withCircuitBreakerErrorThresholdPercentage(10)
                                            //统计滚动的时间窗口
                                            .withMetricsRollingStatisticalWindowInMilliseconds(5000)
                                            //熔断器工作时间，超过这个时间，先放一个请求进去，成功的话就关闭熔断，失败就再等一段时间
                                            .withCircuitBreakerSleepWindowInMilliseconds(2000)
                            )
            );
            this.groupKey = groupKey;
            this.commandKey = commandKey;
            this.poolKey = poolKey;
            this.index = index;
        }

        @Override
        protected String run() throws Exception {
            System.out.println(this.request.getParameterMap());
            if (this.index < 20) {
                throw new Exception("异常...");
            }
            return "NORMAL - " + Thread.currentThread().getName();
        }

        @Override
        protected String getFallback() {
            return "FALLBACK - " + Thread.currentThread().getName();
        }

        public ThreadPoolHystrixCommand setRequest(HttpServletRequest request) {
            this.request = request;
            return this;
        }
    }
}