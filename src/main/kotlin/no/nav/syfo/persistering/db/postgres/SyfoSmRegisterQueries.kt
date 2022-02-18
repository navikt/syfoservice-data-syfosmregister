package no.nav.syfo.persistering.db.postgres

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.Eia
import no.nav.syfo.model.ErIkkeIArbeid
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.Svar
import no.nav.syfo.model.Svartype
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.Sykmeldingsopplysninger
import no.nav.syfo.model.toPGObject
import no.nav.syfo.objectMapper
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel
import no.nav.syfo.sykmelding.model.MottattSykmeldingDbModel
import no.nav.syfo.sykmelding.model.Periode
import no.nav.syfo.sykmelding.model.toEnkelSykmeldingDbModel
import no.nav.syfo.sykmelding.model.toEnkelSykmeldingDbModelUtenStatus
import no.nav.syfo.sykmelding.model.toMotattSykmeldingDbModel
import no.nav.syfo.sykmelding.model.toSendtSykmeldingDbModel
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

fun Connection.getMottattSykmelding(lastMottattTidspunkt: LocalDate): List<MottattSykmeldingDbModel> =
    use {
        this.prepareStatement(
            """
                    SELECT opplysninger.id,
                    pasient_fnr,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                     WHERE opplysninger.mottatt_tidspunkt >= ?
                     AND opplysninger.mottatt_tidspunkt < ?     
                     and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(lastMottattTidspunkt.atStartOfDay()))
            it.setTimestamp(2, Timestamp.valueOf(lastMottattTidspunkt.plusDays(1).atStartOfDay()))
            it.executeQuery().toList { toMotattSykmeldingDbModel() }
        }
    }

fun Connection.getEnkelSykmelding(sykmeldingId: String): EnkelSykmeldingDbModel? =
    use {
        this.prepareStatement(
            """
                    SELECT opplysninger.id,
                    pasient_fnr,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                        INNER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                status.timestamp = (SELECT timestamp
                                                                          FROM sykmeldingstatus
                                                                          WHERE sykmelding_id = opplysninger.id
                                                                          ORDER BY timestamp DESC
                                                                          LIMIT 1) 
                     WHERE opplysninger.id = ?
                    """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { toEnkelSykmeldingDbModel() }.firstOrNull()
        }
    }

fun Connection.getEnkelSykmeldingUtenStatus(sykmeldingId: String): EnkelSykmeldingDbModel? =
    use {
        this.prepareStatement(
            """
                    SELECT opplysninger.id,
                    pasient_fnr,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                     WHERE opplysninger.id = ?
                    """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { toEnkelSykmeldingDbModelUtenStatus() }.firstOrNull()
        }
    }

fun Connection.getSykmeldingMedSisteStatus(lastMottattTidspunkt: LocalDate): List<EnkelSykmeldingDbModel> =
    use {
        this.prepareStatement(
            """
                    SELECT opplysninger.id,
                    pasient_fnr,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.event_timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                        INNER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND status.event = 'SENDT'
                        INNER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                     WHERE opplysninger.mottatt_tidspunkt >= ?
                     AND opplysninger.mottatt_tidspunkt < ?                                                         
                    """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(lastMottattTidspunkt.atStartOfDay()))
            it.setTimestamp(2, Timestamp.valueOf(lastMottattTidspunkt.plusDays(1).atStartOfDay()))
            it.executeQuery().toList { toSendtSykmeldingDbModel() }
        }
    }

fun Connection.getSendtSykmeldingMedSisteStatus(sykmeldingId: String): List<EnkelSykmeldingDbModel> =
    use {
        this.prepareStatement(
            """
                    SELECT opplysninger.id,
                    pasient_fnr,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                        INNER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND status.event = 'SENDT'
                        INNER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                     WHERE opplysninger.id = ?                                                        
                    """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { toSendtSykmeldingDbModel() }
        }
    }

fun Connection.getSykmeldingMedSisteStatusBekreftet(lastMottattTidspunkt: LocalDate): List<EnkelSykmeldingDbModel> =
    use {
        this.prepareStatement(
            """
                    SELECT opplysninger.id,
                    pasient_fnr,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.event_timestamp,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                          INNER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                status.event_timestamp = (SELECT event_timestamp
                                                                          FROM sykmeldingstatus
                                                                          WHERE sykmelding_id = opplysninger.id
                                                                          ORDER BY event_timestamp DESC
                                                                          LIMIT 1) AND
                                                                status.event = 'BEKREFTET'
                     WHERE opplysninger.mottatt_tidspunkt >= ?
                     AND opplysninger.mottatt_tidspunkt < ?                                                         
                    """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(lastMottattTidspunkt.atStartOfDay()))
            it.setTimestamp(2, Timestamp.valueOf(lastMottattTidspunkt.plusDays(1).atStartOfDay()))
            it.executeQuery().toList { toSendtSykmeldingDbModel() }
        }
    }

fun Connection.getSykmeldingMedSisteStatusBekreftet(sykmeldingId: String): EnkelSykmeldingDbModel? =
    use {
        this.prepareStatement(
            """
                    SELECT opplysninger.id,
                    pasient_fnr,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                          INNER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                status.timestamp = (SELECT timestamp
                                                                          FROM sykmeldingstatus
                                                                          WHERE sykmelding_id = opplysninger.id
                                                                          ORDER BY timestamp DESC
                                                                          LIMIT 1) AND
                                                                status.event = 'BEKREFTET'
                     WHERE opplysninger.id = ?
                    """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { toSendtSykmeldingDbModel() }.firstOrNull()
        }
    }

fun Connection.hentSporsmalOgSvar(sykmeldingId: String): List<Sporsmal> =
    use {
        this.prepareStatement(
            """
                    SELECT sporsmal.shortname,
                           sporsmal.tekst,
                           svar.sporsmal_id,
                           svar.svar,
                           svar.svartype,
                           svar.sykmelding_id
                    FROM svar
                             INNER JOIN sporsmal
                                        ON sporsmal.id = svar.sporsmal_id
                    WHERE svar.sykmelding_id = ?
                """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { tilSporsmal() }
        }
    }

fun ResultSet.tilSporsmal(): Sporsmal =
    Sporsmal(
        tekst = getString("tekst"),
        shortName = tilShortName(getString("shortname")),
        svar = tilSvar()
    )

fun ResultSet.tilSvar(): Svar =
    Svar(
        sykmeldingId = getString("sykmelding_id"),
        sporsmalId = getInt("sporsmal_id"),
        svartype = tilSvartype(getString("svartype")),
        svar = getString("svar")
    )

private fun tilShortName(shortname: String): ShortName {
    return when (shortname) {
        "ARBEIDSSITUASJON" -> ShortName.ARBEIDSSITUASJON
        "FORSIKRING" -> ShortName.FORSIKRING
        "FRAVAER" -> ShortName.FRAVAER
        "PERIODE" -> ShortName.PERIODE
        "NY_NARMESTE_LEDER" -> ShortName.NY_NARMESTE_LEDER
        else -> throw IllegalStateException("Sykmeldingen har en ukjent spørsmålskode, skal ikke kunne skje")
    }
}

private fun tilSvartype(svartype: String): Svartype {
    return when (svartype) {
        "ARBEIDSSITUASJON" -> Svartype.ARBEIDSSITUASJON
        "PERIODER" -> Svartype.PERIODER
        "JA_NEI" -> Svartype.JA_NEI
        else -> throw IllegalStateException("Sykmeldingen har en ukjent svartype, skal ikke kunne skje")
    }
}

fun Connection.lagreBehandlingsutfallAndCommit(behandlingsutfall: Behandlingsutfall) =
    use { connection ->
        lagreBehandlingsutfall(connection, behandlingsutfall)
        connection.commit()
    }

fun lagreBehandlingsutfall(
    connection: Connection,
    behandlingsutfall: Behandlingsutfall
) {
    connection.prepareStatement(
        """
                    INSERT INTO BEHANDLINGSUTFALL(id, behandlingsutfall) VALUES (?, ?) ON CONFLICT DO NOTHING
                """
    ).use {
        it.setString(1, behandlingsutfall.id)
        it.setObject(2, behandlingsutfall.behandlingsutfall.toPGObject())
        it.executeUpdate()
    }
}

fun DatabaseInterfacePostgres.oppdaterBehandlingsutfall(behandlingsutfall: Behandlingsutfall) {
    this.connection.use { connection ->
        connection.prepareStatement(
            """
                UPDATE BEHANDLINGSUTFALL
                SET behandlingsutfall = ?
                WHERE
                id = ?
            """
        ).use {
            it.setObject(1, behandlingsutfall.behandlingsutfall.toPGObject())
            it.setString(2, behandlingsutfall.id)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun Connection.oppdaterSykmeldingsopplysninger(listEia: List<Eia>) {
    use { connection ->
        connection.prepareStatement(
            """
                UPDATE SYKMELDINGSOPPLYSNINGER
                SET lege_fnr = ?,
                    legekontor_org_nr = ?,
                    legekontor_her_id = ?,
                    legekontor_resh_id = ?
                WHERE
                mottak_id = ?
            """
        ).use {
            for (eia in listEia) {
                it.setString(1, eia.legefnr)
                it.setString(2, eia.legekontorOrgnr)
                it.setString(3, eia.legekontorHer)
                it.setString(4, eia.legekontorResh)
                it.setString(5, eia.mottakid)
                it.addBatch()
            }
            it.executeBatch()
        }
        connection.commit()
    }
}

fun Connection.hentSykmeldingsdokument(sykmeldingid: String): Sykmeldingsdokument? =
    use { connection ->
        connection.prepareStatement(
            """
                select * from sykmeldingsdokument where id = ?
            """
        ).use {
            it.setString(1, sykmeldingid)
            it.executeQuery().toSykmeldingsdokument()
        }
    }

fun Connection.hentSykmeldingMedBehandlingsutfallForId(id: String): List<SykmeldingDokumentBehandlingsutfallDbModel> =
    use { connection ->
        connection.prepareStatement(
            """
                select * from sykmeldingsopplysninger sm 
                LEFT OUTER JOIN sykmeldingsdokument sd on sm.id = sd.id 
                LEFT OUTER JOIN behandlingsutfall bu on sm.id = bu.id
                where sm.id = ?
                and sm.epj_system_navn!='SYFOSERVICE' 
                AND sm.mottatt_tidspunkt < '2019-12-19' 
                AND sm.mottatt_tidspunkt >= '2019-10-07'
            """
        ).use {
            it.setString(1, id)
            it.executeQuery().toList { toSykmeldingDokumentBehandlingsutfall() }
        }
    }

fun Connection.hentSykmeldingIdManglerBehandlingsutfall(msgId: String): String? =
    use { connection ->
        connection.prepareStatement(
            """
                select sd.id from sykmeldingsdokument sd
                where NOT exists(select 1 from behandlingsutfall where id = sd.id) AND sd.sykmelding->>'msgId' = ?;
            """
        ).use {
            it.setString(1, msgId)
            it.executeQuery().getId()
        }
    }

fun ResultSet.getId(): String? {
    return if (next()) {
        getString("id")
    } else null
}

fun Connection.hentSykmeldingMedId(sykmeldingId: String): SykmeldingDbModel? =
    use { connection ->
        connection.prepareStatement(
            """
                select * from sykmeldingsopplysninger sm 
                INNER JOIN sykmeldingsdokument sd on sm.id = sd.id
                WHERE sm.id = ?
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toSykmelding()
        }
    }

fun ResultSet.toSykmelding(): SykmeldingDbModel? {
    if (next()) {
        val sykmeldingId = getString("id")
        val sykmeldingsdokument =
            getNullsafeSykmeldingsdokument(sykmeldingId)
        val sykmeldingsopplysninger = Sykmeldingsopplysninger(
            id = sykmeldingId,
            mottakId = getString("mottak_id"),
            pasientFnr = getString("pasient_fnr"),
            pasientAktoerId = getString("pasient_aktoer_id"),
            legeFnr = getString("lege_fnr"),
            legeAktoerId = getString("lege_aktoer_id"),
            legekontorOrgNr = getString("legekontor_org_nr"),
            legekontorHerId = getString("legekontor_her_id"),
            legekontorReshId = getString("legekontor_resh_id"),
            epjSystemNavn = getString("epj_system_navn"),
            epjSystemVersjon = getString("epj_system_versjon"),
            mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
            tssid = getString("tss_id")
        )
        return SykmeldingDbModel(sykmeldingsopplysninger, sykmeldingsdokument)
    }
    return null
}

fun ResultSet.toSykmeldingsdokument(): Sykmeldingsdokument? {
    if (next()) {
        val sykmeldingId = getString("id")
        return getNullsafeSykmeldingsdokument(sykmeldingId)
    }
    return null
}

private fun ResultSet.getNullsafeSykmeldingsdokument(sykmeldingId: String): Sykmeldingsdokument? {
    val sykmeldingDokument = getString("sykmelding")
    if (sykmeldingDokument.isNullOrEmpty()) {
        return null
    }
    return Sykmeldingsdokument(sykmeldingId, objectMapper.readValue(getString("sykmelding")))
}

fun ResultSet.toSykmeldingDokumentBehandlingsutfall(): SykmeldingDokumentBehandlingsutfallDbModel {
    val sykmeldingId = getString("id")
    val sykmeldingsDokument = this.getNullsafeSykmeldingsdokument(sykmeldingId)
    val sykmeldingMedBehandlingsutfall = this.toSykmeldingMedBehandlingsutfall()

    return SykmeldingDokumentBehandlingsutfallDbModel(
        sykmeldingMedBehandlingsutfall.sykmeldingsopplysninger,
        sykmeldingsDokument,
        sykmeldingMedBehandlingsutfall.behandlingsutfall
    )
}

fun ResultSet.toSykmeldingMedBehandlingsutfall(): SykmeldingBehandlingsutfallDbModel {
    val sykmeldingId = getString("id")
    val behandlingsutfall = getBehandlingsutfall(sykmeldingId)
    val sykmeldingsopplysninger = Sykmeldingsopplysninger(
        id = sykmeldingId,
        mottakId = getString("mottak_id"),
        pasientFnr = getString("pasient_fnr"),
        pasientAktoerId = getString("pasient_aktoer_id"),
        legeFnr = getString("lege_fnr"),
        legeAktoerId = getString("lege_aktoer_id"),
        legekontorOrgNr = getString("legekontor_org_nr"),
        legekontorHerId = getString("legekontor_her_id"),
        legekontorReshId = getString("legekontor_resh_id"),
        epjSystemNavn = getString("epj_system_navn"),
        epjSystemVersjon = getString("epj_system_versjon"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
        tssid = getString("tss_id")
    )
    return SykmeldingBehandlingsutfallDbModel(sykmeldingsopplysninger, behandlingsutfall)
}

private fun ResultSet.getBehandlingsutfall(sykmeldingId: String): Behandlingsutfall? {
    if (next()) {
        val behandlingsutfallString = getString("behandlingsutfall")
        val behandlingsutfall = if (behandlingsutfallString != null) Behandlingsutfall(
            sykmeldingId,
            objectMapper.readValue(behandlingsutfallString)
        ) else null
        return behandlingsutfall
    } else {
        return null
    }
}

fun DatabasePostgres.updateDiagnose(diagnose: Diagnosekoder.DiagnosekodeType, sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{medisinskVurdering,hovedDiagnose}', ?::jsonb) where id = ?;
        """
        ).use {
            val diag = Diagnose(diagnose.oid, diagnose.code, diagnose.text)
            it.setString(1, objectMapper.writeValueAsString(diag))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}

fun DatabasePostgres.updateBiDiagnose(diagnoser: List<Diagnosekoder.DiagnosekodeType>, sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{medisinskVurdering,biDiagnoser}', ?::jsonb) where id = ?;
        """
        ).use {
            val biDiagnoser = diagnoser.map { diagnose -> Diagnose(diagnose.oid, diagnose.code, diagnose.text) }
            it.setString(1, objectMapper.writeValueAsString(biDiagnoser))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}

fun DatabasePostgres.updateSvangerskap(sykmeldingId: String, svangerskap: Boolean): Int {
    var updated: Int
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{medisinskVurdering,svangerskap}', ?::jsonb) where id = ?;
        """
        ).use {
            it.setString(1, svangerskap.toString())
            it.setString(2, sykmeldingId)
            updated = it.executeUpdate()
        }
        connection.commit()
        return updated
    }
}

fun DatabasePostgres.updateBehandletTidspunkt(sykmeldingId: String, behandletTidspunkt: LocalDateTime) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{behandletTidspunkt}', ?::jsonb) where id = ?;
        """
        ).use {
            it.setString(1, objectMapper.writeValueAsString(behandletTidspunkt))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}

fun DatabasePostgres.updateSkjermesForPasient(sykmeldingId: String, skjermet: Boolean): Int {
    var updated: Int
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{medisinskVurdering,skjermetForPasient}', ?::jsonb) where id = ?;
        """
        ).use {
            it.setString(1, skjermet.toString())
            it.setString(2, sykmeldingId)
            updated = it.executeUpdate()
        }
        connection.commit()
        return updated
    }
}

fun DatabasePostgres.updatePeriode(periodeListe: List<Periode>, sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{perioder}', ?::jsonb) where id = ?;
        """
        ).use {
            it.setString(1, objectMapper.writeValueAsString(periodeListe))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}

fun DatabasePostgres.updateErIkkeIArbeid(sykmeldingId: String, erIkkeIArbeid: ErIkkeIArbeid?) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{prognose,erIkkeIArbeid}', ?::jsonb) where id = ?;
        """
        ).use {
            it.setString(1, objectMapper.writeValueAsString(erIkkeIArbeid))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}

fun Connection.getSykmeldingWithIArbeidIkkeIArbeid(): List<Sykmelding> {
    use {
        this.prepareStatement(
            """
            select sykmelding from sykmeldingsopplysninger so
                inner join sykmeldingsdokument sd on sd.id = so.id
            where epj_system_navn = 'Papirsykmelding' and mottatt_tidspunkt > '2020-09-28' and sykmelding->'prognose'->>'erIArbeid' is not null and sykmelding->'prognose'->>'erIkkeIArbeid' is not null;
        """
        ).use {
            return it.executeQuery().toList { getSykmeldingdocument() }
        }
    }
}

fun Connection.getSykmeldingWithEmptyUtdypendeOpplysninger(): List<Sykmelding> {
    use {
        this.prepareStatement(
            """
            select sykmelding from sykmeldingsopplysninger so 
            inner join sykmeldingsdokument sd on sd.id = so.id
            where epj_system_navn = 'Papirsykmelding' and mottatt_tidspunkt > '2020-09-28' and sykmelding->>'utdypendeOpplysninger' != '{}' and sykmelding->>'utdypendeOpplysninger' LIKE '%' || '{}' || '%';
        """
        ).use {
            return it.executeQuery().toList { getSykmeldingdocument() }
        }
    }
}

private fun ResultSet.getSykmeldingdocument(): Sykmelding {
    return objectMapper.readValue(getString("sykmelding"))
}

fun DatabasePostgres.updatePrognose(sykmeldingId: String, prognose: Prognose?) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{prognose}', ?::jsonb) where id = ?;
        """
        ).use {
            it.setString(1, objectMapper.writeValueAsString(prognose))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}

fun DatabasePostgres.updateUtdypendeOpplysninger(
    sykmeldingId: String,
    utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>
) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE sykmeldingsdokument set sykmelding = jsonb_set(sykmelding, '{utdypendeOpplysninger}', ?::jsonb) where id = ?;
        """
        ).use {
            it.setString(1, objectMapper.writeValueAsString(utdypendeOpplysninger))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}
