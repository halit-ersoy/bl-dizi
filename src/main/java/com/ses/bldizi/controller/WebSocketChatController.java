package com.ses.bldizi.controller;

import com.ses.bldizi.model.Message;
import com.ses.bldizi.model.Person;
import com.ses.bldizi.repository.MessageRepository;
import com.ses.bldizi.repository.PersonRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
public class WebSocketChatController {

    private final MessageRepository messageRepository;
    private final PersonRepository personRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketChatController(MessageRepository messageRepository,
                                   PersonRepository personRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.personRepository = personRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendWebSocketMessage(Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null || !sessionAttributes.containsKey("userId")) {
            return; // Unauthorized
        }

        try {
            UUID senderId = UUID.fromString(sessionAttributes.get("userId").toString());
            String senderNickname = sessionAttributes.get("nickname").toString();

            String receiverNickname = payload.get("receiverNickname");
            String content = payload.get("content");

            if (content == null || content.trim().isEmpty() || content.length() > 400) {
                return; // Invalid content
            }

            Optional<Person> receiver = personRepository.findByNicknameOrEmail(receiverNickname);
            if (receiver.isEmpty() || !receiver.get().isAllowMessages()) {
                return; // Receiver not found or blocked messages
            }

            if (messageRepository.isBlocked(senderId, receiver.get().getId())) {
                return; // User is blocked
            }

            // Save to database using the repository
            messageRepository.sendMessage(senderId, receiver.get().getId(), content);

            // Fetch the chat history to retrieve the newly saved message (with correct database ID and timestamp)
            List<Message> history = messageRepository.getChatHistory(senderId, receiver.get().getId());
            if (!history.isEmpty()) {
                Message savedMessage = history.get(history.size() - 1);
                if (savedMessage != null) {
                    Object msgObj = savedMessage;
                    // Publish message to both receiver and sender topics
                    messagingTemplate.convertAndSend("/topic/messages/" + receiverNickname, msgObj);
                    messagingTemplate.convertAndSend("/topic/messages/" + senderNickname, msgObj);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing WebSocket send message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @MessageMapping("/chat.read")
    public void readWebSocketMessage(Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null || !sessionAttributes.containsKey("userId")) {
            return; // Unauthorized
        }

        try {
            UUID myId = UUID.fromString(sessionAttributes.get("userId").toString());
            String myNickname = sessionAttributes.get("nickname").toString();
            String otherNickname = payload.get("otherNickname");

            if (otherNickname == null || otherNickname.trim().isEmpty()) {
                return;
            }

            Optional<Person> otherUser = personRepository.findByNicknameOrEmail(otherNickname);
            if (otherUser.isPresent()) {
                // Mark all unread messages from otherUser to me as read
                messageRepository.markAsRead(otherUser.get().getId(), myId);

                // Send real-time read event notification to the other user's topic
                Object readEventPayload = Map.of(
                        "type", "READ_EVENT",
                        "readBy", myNickname
                );
                messagingTemplate.convertAndSend("/topic/messages/" + otherNickname, readEventPayload);
            }
        } catch (Exception e) {
            System.err.println("Error processing WebSocket read status: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
