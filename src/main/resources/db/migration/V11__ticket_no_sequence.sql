-- Sequential, collision-free ticket number generation
CREATE SEQUENCE IF NOT EXISTS ticket_no_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;
