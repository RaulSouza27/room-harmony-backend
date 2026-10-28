package com.clinica.escuta.service;

import com.clinica.escuta.DTO.ReservationDTO;
import com.clinica.escuta.model.Room;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.RoomRepository;
import com.clinica.escuta.repository.UnitRepository;
import com.clinica.escuta.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class TelegramNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificationService.class);

    @Value("${telegram.bot.enabled:true}")
    private boolean enabled;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.chat-id:}")
    private String chatId;

    @Value("${telegram.bot.api-url:https://api.telegram.org/bot}")
    private String apiUrl;

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final UnitRepository unitRepository;
    private final RestTemplate restTemplate;

    public TelegramNotificationService(
            UserRepository userRepository,
            RoomRepository roomRepository,
            UnitRepository unitRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.unitRepository = unitRepository;
        this.restTemplate = new RestTemplate();
    }

    @Async
    public void notifyNewReservation(ReservationDTO reservationDTO, boolean isSpecialAdmin) {
        if (!enabled || botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            logger.info("Notificação do Telegram ignorada: token ou chat-id não configurado.");
            return;
        }

        try {
            Optional<User> userOpt = userRepository.findById(reservationDTO.getUserId());
            Optional<Room> roomOpt = roomRepository.findById(reservationDTO.getRoomsId());

            String userName = userOpt.map(User::getUsername).orElse("Profissional #" + reservationDTO.getUserId());
            String roomName = roomOpt.map(Room::getName).orElse("Sala #" + reservationDTO.getRoomsId());

            String unitName = "—";
            if (roomOpt.isPresent()) {
                Optional<Unit> unitOpt = unitRepository.findById(roomOpt.get().getUnitId());
                unitName = unitOpt.map(Unit::getName).orElse("—");
            }

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String dataFormatada = reservationDTO.getData() != null ? reservationDTO.getData().format(dateFormatter) : "—";
            String statusUpper = reservationDTO.getStatusString() != null ? reservationDTO.getStatusString().toUpperCase() : "PENDENTE";

            StringBuilder message = new StringBuilder();
            if (isSpecialAdmin) {
                message.append("✨ <b>NOVA RESERVA ESPECIAL (ADMIN)</b> ✨\n\n");
            } else {
                message.append("📌 <b>NOVA SOLICITAÇÃO DE RESERVA</b>\n\n");
            }

            message.append("👤 <b>Profissional:</b> ").append(escapeHtml(userName)).append("\n");
            message.append("🏢 <b>Unidade:</b> ").append(escapeHtml(unitName)).append("\n");
            message.append("🚪 <b>Sala:</b> ").append(escapeHtml(roomName)).append("\n");
            message.append("📅 <b>Data:</b> ").append(dataFormatada).append("\n");
            message.append("⏰ <b>Horário:</b> ").append(reservationDTO.getHoraInicio()).append(" às ").append(reservationDTO.getHoraFim()).append("\n");
            message.append("⚡ <b>Status:</b> ").append(statusUpper).append("\n");

            if (reservationDTO.getDescription() != null && !reservationDTO.getDescription().isBlank()) {
                message.append("📝 <b>Obs:</b> ").append(escapeHtml(reservationDTO.getDescription())).append("\n");
            }

            message.append("\n👉 <i>Acesse o painel do sistema para analisar ou visualizar os detalhes.</i>");

            String url = apiUrl + botToken.trim() + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId.trim());
            body.put("text", message.toString());
            body.put("parse_mode", "HTML");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);

            logger.info("Notificação de reserva enviada para o grupo do Telegram com sucesso.");
        } catch (Exception e) {
            logger.error("Falha ao enviar mensagem para a API do Telegram: {}", e.getMessage(), e);
        }
    }

    @Async
    public void notifyNewCreditPackage(com.clinica.escuta.DTO.CreditPackageDTO pkgDto) {
        if (!enabled || botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            logger.info("Notificação do Telegram ignorada: token ou chat-id não configurado.");
            return;
        }

        try {
            StringBuilder message = new StringBuilder();
            message.append("💳 <b>NOVA SOLICITAÇÃO DE PACOTE DE CRÉDITOS (12h)</b>\n\n");
            message.append("👤 <b>Profissional:</b> ").append(escapeHtml(pkgDto.getUserName())).append("\n");
            message.append("🏢 <b>Unidade:</b> ").append(escapeHtml(pkgDto.getUnitName())).append("\n");
            message.append("⏱️ <b>Créditos:</b> ").append(pkgDto.getTotalCredits()).append(" horas\n");
            message.append("⚡ <b>Status:</b> PENDENTE\n\n");
            message.append("👉 <i>Acesse o painel do sistema para analisar o comprovante e aprovar ou rejeitar o pacote.</i>");

            String url = apiUrl + botToken.trim() + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId.trim());
            body.put("text", message.toString());
            body.put("parse_mode", "HTML");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);

            logger.info("Notificação de pacote de créditos enviada para o grupo do Telegram com sucesso.");
        } catch (Exception e) {
            logger.error("Falha ao enviar mensagem de pacote de créditos para o Telegram: {}", e.getMessage(), e);
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}
