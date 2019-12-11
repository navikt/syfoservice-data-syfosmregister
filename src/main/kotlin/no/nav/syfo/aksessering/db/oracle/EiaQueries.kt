package no.nav.syfo.aksessering.db.oracle

import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.Eia
import java.sql.ResultSet

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
                AND EDILOGGID IN ('1607220943tnsb16826.1','1608091611tnsb80090.1','1609161212fysi65610.1','1612051517fysi44346.1','1612201600fysi82931.1','1701021942fysi65032.1','1701121217inst64086.1','1701131524fysi38965.1','1701161254fysi57359.1','1701201600fysi43419.1','1701311705fysi68270.1','1702031612fysi90068.1','1702151236fysi74724.1','1702171524fysi00645.1','1702171550fysi20651.1','1702231621trim93212.1','1702241659trim41867.1','1705241441kiro90605.1','1705241441kiro90617.1','1909091109holm02426.1','1909271110vest10640.1')
                AND MELDING_TYPE_VERSJON = '2013-10-01'
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

        } catch (e: Exception) {
            log.warn("Sykmelding feiler på mapping med Ediloggid: $ediLoggId", e)
        }
    }
    return DatabaseResult(lastIndex, listEia)
}

private fun getOrgNumber(resultSet: ResultSet): String? =
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
