package com.kh.back.common.util;

import com.kh.back.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiResponseWriter {
    private final ObjectMapper objectMapper;

    // MVC 바깥의 Security 오류도 컨트롤러와 동일한 JSON 형식으로 전달한다.
    public void write(HttpServletResponse response, int status, ApiResponse<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
