CREATE TABLE arkivpost(
    arkivpostid NUMERIC NOT NULL PRIMARY KEY,
    arkivertdato TIMESTAMP,
    mottattdato TIMESTAMP,
    utgaardato TIMESTAMP,
    temagruppe VARCHAR,
    arkivposttype VARCHAR,
    dokumenttype VARCHAR,
    kryssreferanseid VARCHAR,
    kanal VARCHAR,
    aktoerid VARCHAR,
    fodselsnummer VARCHAR,
    navident VARCHAR,
    innhold VARCHAR,
    journalfoerendeenhet VARCHAR,
    status VARCHAR,
    kategorikode VARCHAR,
    signert NUMERIC,
    erorganinternt NUMERIC,
    begrensetpartinnsyn NUMERIC,
    sensitiv NUMERIC
);

CREATE TABLE vedlegg(
    arkivpostid NUMERIC NOT NULL PRIMARY KEY,
    filnavn VARCHAR,
    filtype VARCHAR,
    variantformat VARCHAR,
    tittel VARCHAR,
    brevkode VARCHAR,
    strukturert NUMERIC,
    dokument TEXT
);