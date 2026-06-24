package com.example.demos.web.service;

import lombok.AllArgsConstructor;
import lombok.Data;

public interface WebSocketService {



    void sendMessageToUser(Long userId, String message);



}
