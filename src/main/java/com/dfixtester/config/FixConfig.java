package com.dfixtester.config;

import com.dfixtester.engine.FixApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import quickfix.*;

import java.io.InputStream;

@Configuration
public class FixConfig {

    @Bean
    public ThreadedSocketInitiator threadedSocketInitiator(FixApplication fixApplication) throws Exception {
        InputStream inputStream = new ClassPathResource("quickfix-client.cfg").getInputStream();
        SessionSettings settings = new SessionSettings(inputStream);
        
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        ThreadedSocketInitiator initiator = new ThreadedSocketInitiator(
                fixApplication, storeFactory, settings, logFactory, messageFactory);
        
        initiator.start();
        return initiator;
    }
}
