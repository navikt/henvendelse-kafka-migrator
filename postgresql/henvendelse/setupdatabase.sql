CREATE TABLE henvendelse(
    henvendelse_id NUMERIC NOT NULL PRIMARY KEY,
    behandlingsid  VARCHAR NOT NULL,
    behandlingskjedeid  VARCHAR,
    type  VARCHAR NOT NULL,
    tema  VARCHAR,
    aktor  VARCHAR,
    status  VARCHAR,
    opprettetdato  TIMESTAMP NOT NULL,
    innsendtdato  VARCHAR,
    sistendretdato  VARCHAR,
    behandlingsresultat TEXT,
    journalfortsaksid  VARCHAR,
    journalforttema  VARCHAR,
    journalpostid  VARCHAR,
    batch_status VARCHAR DEFAULT 'LEDIG',
    arkivpostid  VARCHAR,
    kontorsperre  VARCHAR,
    oppgaveidgsak  VARCHAR,
    henvendelseidgsak  VARCHAR,
    eksternaktor  VARCHAR,
    tilknyttetenhet  VARCHAR,
    ertilknyttetansatt  NUMERIC,
    brukersenhet VARCHAR,
    korrelasjonsid VARCHAR,
    oversendtdokmot NUMERIC,
    behandlingstema VARCHAR
);
CREATE TABLE hendelse(
    id NUMERIC NOT NULL PRIMARY KEY,
    henvendelse_id NUMERIC NOT NULL,
    aktor VARCHAR NOT NULL,
    type VARCHAR NOT NULL,
    dato TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    enhet VARCHAR,
    verdi VARCHAR
);

CREATE TABLE migreringmetadata(
    key VARCHAR,
    value VARCHAR
);

CREATE INDEX henvendelse_henvendelse_id on henvendelse (henvendelse_id);
CREATE INDEX hendelse_henvendelse_id on hendelse (henvendelse_id);