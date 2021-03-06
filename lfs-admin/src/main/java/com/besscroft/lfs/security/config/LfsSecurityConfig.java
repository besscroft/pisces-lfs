package com.besscroft.lfs.security.config;

import com.besscroft.lfs.security.component.DynamicAccessDecisionManager;
import com.besscroft.lfs.security.component.DynamicSecurityMetadataSource;
import com.besscroft.lfs.security.filter.AuthenticationFilter;
import com.besscroft.lfs.security.filter.DynamicSecurityFilter;
import com.besscroft.lfs.security.handler.LfsDeniedHandler;
import com.besscroft.lfs.security.handler.LfsEntryPoint;
import com.besscroft.lfs.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * @Author Bess Croft
 * @Time 2021/7/8 17:45
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class LfsSecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private UserService userService;

    @Bean
    public UserDetailsService userDetailsService() {
        //????????????????????????
        return userService::loadUserByUsername;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry = http
                .authorizeRequests();
        // ??????????????????????????????????????????,???????????????????????????????????????????????????
        for (String url : ignoreUrlsConfig().getUrls()) {
            registry.antMatchers(url).permitAll();
        }
        //?????????????????????OPTIONS??????
        registry.antMatchers(HttpMethod.OPTIONS)
                .permitAll();

        // ??????????????????????????????
        registry.and()
                .authorizeRequests()
                .anyRequest()
                .authenticated()
                // ????????????????????????????????????session
                .and()
                .csrf()
                .disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                // ???????????????????????????
                .and()
                .exceptionHandling()
                .accessDeniedHandler(lfsDeniedHandler())
                .authenticationEntryPoint(lfsEntryPoint())
                // ????????????????????????JWT?????????
                .and()
                .addFilterBefore(authenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        // ???????????????????????????
        registry.and().addFilterBefore(dynamicSecurityFilter(), FilterSecurityInterceptor.class);
    }

    @Bean
    public LfsDeniedHandler lfsDeniedHandler() {
        return new LfsDeniedHandler();
    }

    @Bean
    public LfsEntryPoint lfsEntryPoint() {
        return new LfsEntryPoint();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // ??????UserDetailService????????????
        auth.userDetailsService(userDetailsService()).passwordEncoder(passwordEncoder());
    }

    @Bean
    @Override
    protected AuthenticationManager authenticationManager() throws Exception {
        return super.authenticationManager();
    }

    @Bean
    public AuthenticationFilter authenticationFilter() {
        return new AuthenticationFilter();
    }

    @Bean
    public IgnoreUrlsConfig ignoreUrlsConfig() {
        return new IgnoreUrlsConfig();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DynamicAccessDecisionManager dynamicAccessDecisionManager() {
        return new DynamicAccessDecisionManager();
    }

    @Bean
    public DynamicSecurityFilter dynamicSecurityFilter() {
        return new DynamicSecurityFilter();
    }

    @Bean
    public DynamicSecurityMetadataSource dynamicSecurityMetadataSource() {
        return new DynamicSecurityMetadataSource();
    }

}
