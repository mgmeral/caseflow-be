package com.caseflow.email.api.mapper;

import com.caseflow.email.api.dto.MailboxRequest;
import com.caseflow.email.api.dto.MailboxResponse;
import com.caseflow.email.domain.EmailMailbox;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailMailboxMapper {

    public EmailMailbox toEntity(MailboxRequest request) {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setName(request.name());
        mailbox.setDisplayName(request.displayName());
        mailbox.setAddress(request.address());
        mailbox.setProviderType(request.providerType());
        mailbox.setInboundMode(request.inboundMode());
        mailbox.setOutboundMode(request.outboundMode());
        mailbox.setIsActive(request.isActive() != null ? request.isActive() : Boolean.TRUE);
        mailbox.setDefaultGroupId(request.defaultGroupId());
        mailbox.setDefaultPriority(request.defaultPriority());
        mailbox.setSmtpHost(request.smtpHost());
        mailbox.setSmtpPort(request.smtpPort());
        mailbox.setSmtpUsername(request.smtpUsername());
        mailbox.setSmtpPassword(request.smtpPassword());
        mailbox.setSmtpUseSsl(request.smtpUseSsl() != null ? request.smtpUseSsl() : Boolean.FALSE);
        return mailbox;
    }

    /** Never exposes smtpPassword — passwords are write-only. */
    public MailboxResponse toResponse(EmailMailbox mailbox) {
        return new MailboxResponse(
                mailbox.getId(),
                mailbox.getName(),
                mailbox.getDisplayName(),
                mailbox.getAddress(),
                mailbox.getProviderType(),
                mailbox.getInboundMode(),
                mailbox.getOutboundMode(),
                mailbox.getIsActive(),
                mailbox.getDefaultGroupId(),
                mailbox.getDefaultPriority(),
                mailbox.getSmtpHost(),
                mailbox.getSmtpPort(),
                mailbox.getSmtpUsername(),
                mailbox.getSmtpUseSsl(),
                mailbox.getLastSuccessfulInboundAt(),
                mailbox.getLastSuccessfulOutboundAt(),
                mailbox.getCreatedAt(),
                mailbox.getUpdatedAt()
        );
    }

    public List<MailboxResponse> toResponseList(List<EmailMailbox> mailboxes) {
        return mailboxes.stream().map(this::toResponse).toList();
    }
}
