package com.kh.back.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ChatbotAsyncConfig {

    @Bean(name = "chatbotTaskExecutor")
    public ThreadPoolTaskExecutor chatbotTaskExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor(); // 챗봇작업을 별도 스레드에서 실행할 도구를 만들고 spring 에 등록 하는 설정


        executor.setCorePoolSize(2); // 작업을 처리할 기본 스레드 수를 2개로 설정
        executor.setMaxPoolSize(4); // 작업이 밀리면 스레드를 최대 4개까지 늘릴수 있음
        executor.setQueueCapacity(20); // 스레드가 작업중일때 대기할 작업을 최대 20개까지 보관
        executor.setThreadNamePrefix("chatbot-"); // 스레드 이름 앞에 chatbot-을 붙이고. chatbot-1, chat-2로 표시돼 로그에서 챗봇작업을 구분하기 쉬움


        executor.setWaitForTasksToCompleteOnShutdown(true); // 서버를 종료할떄 실행중인 작업을 강제로 중단하지 않고, 대기열에 있는 작업도 처리하도록 설정
        executor.setAwaitTerminationSeconds(30); // 서버 종료 과정에서 작업이 끝나기를 최대 30초 동안 기다림

        return executor; // 설정을 마친 실행 도구를 반환,  spring 은 이 객체를 앞서 지정한 chatbottaskexecutor 라는 이름으로 등록해 다른 클래스에서 사용할수 있게함
    }
}