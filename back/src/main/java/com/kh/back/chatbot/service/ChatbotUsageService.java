package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotUsageDto;

public interface ChatbotUsageService {

    ChatbotUsageDto getUsage(CustomUserDetails principal);

    ChatbotUsageDto reserveUsage(CustomUserDetails principal);
}