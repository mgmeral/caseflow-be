Email to Ticket flow:

1. Receive inbound email (IMAP or webhook)
2. Parse email content
3. Save raw email to MongoDB (EmailDocument)
4. Extract attachments and upload to object storage
5. Resolve thread using:
    - message-id
    - in-reply-to
    - references
6. If thread matches → link to existing ticket
7. If not → create new ticket
8. Perform customer matching:
    - contact email
    - domain
9. Create history record
10. Attach email to ticket

Rules:
- Do not rely only on subject for thread matching
- Avoid duplicate ticket creation
- Email is source of truth for inbound communication