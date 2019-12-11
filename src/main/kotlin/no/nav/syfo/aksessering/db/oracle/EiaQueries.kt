package no.nav.syfo.aksessering.db.oracle

import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.Eia

fun DatabaseInterfaceOracle.hentSykmeldingerEia(lastIndex: Int, limit: Int): DatabaseResult<Eia> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT *
                    FROM melding
                    WHERE melding_type_kode = 'SYKMELD'
                    AND MELDING_ID > ?
                    AND MELDING_TYPE_VERSJON = '2013-10-01'
                    ORDER BY melding_id ASC
                    FETCH NEXT ? ROWS ONLY
                        """
        ).use {
            it.setInt(1, lastIndex)
            it.setInt(2, limit)
            val resultSet = it.executeQuery()
            val databaseResult = resultSet.toEia(lastIndex)
            return databaseResult
        }
    }

fun DatabaseInterfaceOracle.hentSykmeldingerEia(): DatabaseResult<Eia> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM EIA2_1_P.melding
                WHERE melding_type_kode = 'SYKMELD'
                AND EDILOGGID IN ('1807230903lard09813.1','1808141146kara71100.1','1805310956hovl58800.1','1801251020samn87865.1','1810081533bogo75704.1','1811131112stry46640.1','1811281318bogo95925.1','1811301007stry25425.1')
                AND MELDING_TYPE_VERSJON = '2013-10-01';
            """
        ).use {
            val resultSet = it.executeQuery()
            return resultSet.toEia(0)
        }
    }

fun ResultSet.toEia(previusIndex: Int): DatabaseResult<Eia> {

    val listEia = ArrayList<Eia>()
    var lastIndex = previusIndex

    while (next()) {
        val ediLoggId = getString("EDILOGGID")
        lastIndex = getInt("MELDING_ID")
        try {
            val legekontorOrgName = getString("ORGANISASJON_NAVN")
            val personNumberPatient = getString("PASIENT_ID")
            val personNumberDoctor = getString("AVSENDER_FNRSIGNATUR")

            if (validatePersonNumber(personNumberPatient)) {
                listEia.add(
                    Eia(
                        pasientfnr = personNumberPatient,
                        legefnr = setPersonNumberDoctor(personNumberDoctor),
                        mottakid = ediLoggId,
                        legekontorOrgnr = getOrgNumber(this),
                        legekontorHer = getHEROrg(this),
                        legekontorResh = getRSHOrg(this),
                        legekontorOrgnavn = legekontorOrgName

                    )
                )
            } else {
                log.warn("Ugyldig fnr på pasient med Ediloggid: $ediLoggId")
            }
        } catch (e: Exception) {
            log.warn("Sykmelding feiler på mapping med Ediloggid: $ediLoggId", e)
        }
    }
    return DatabaseResult(lastIndex, listEia)
}

private fun getOrgNumber(resultSet: ResultSet): String =
    when (resultSet.getString("ORGANISASJON_IDTYPE")) {
        "ENH" -> resultSet.getString("ORGANISASJON_ID")
        else -> ""
    }

private fun getHEROrg(resultSet: ResultSet): String =
    when (resultSet.getString("ORGANISASJON_IDTYPE")) {
        "HER" -> resultSet.getString("ORGANISASJON_ID")
        else -> ""
    }

private fun getRSHOrg(resultSet: ResultSet): String =
    when (resultSet.getString("ORGANISASJON_IDTYPE")) {
        "RSH" -> resultSet.getString("ORGANISASJON_ID")
        else -> ""
    }

private fun setPersonNumberDoctor(personNumberDoctor: String?): String {
    return if (personNumberDoctor.isNullOrEmpty()) {
        ""
    } else {
        personNumberDoctor
    }
}

private fun validatePersonNumber(personNumber: String?): Boolean {
    return !personNumber.isNullOrEmpty() && personNumber.length == 11
}

fun DatabaseInterfaceOracle.hentAntallSykmeldingerEia(): List<AntallSykmeldinger> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT COUNT(MELDING_ID) as antall
                    FROM melding
                    WHERE melding_type_kode = 'SYKMELD'
                    AND MELDING_TYPE_VERSJON = '2013-10-01'
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldinger() }
        }
    }
