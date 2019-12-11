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
                AND EDILOGGID IN ('1909271110vest10640.1','1909111514bogo12699.1','1909091109holm02426.1','1909051530bogo11270.1','1908291433lofo68736.1','1908121156otta44619.1','1908121036bogo40819.1','1908091053land37853.1','1908090949rana79464.1','1908071200ryke26180.1','1907311518bysk60688.1','1907301416hjel61773.1','1907261058ryke37867.1','1907151310bogo85712.1','1907051324aske45623.1','1906241143hyan97928.1','1906171134bogo35702.1','1906171131bogo31227.1','1906040953aurl62086.1','1905281142hyan72743.1','1905221608bogo25719.1','1904291116hyan71156.1','1904251328bogo93887.1','1904011354hyan67283.1','1903281709bogo58670.1','1903281407pors83153.1','1903271528sund98507.1','1903181211skik98844.1','1903121042hyan29473.1','1903071223bogo67946.1','1903041721lofo90576.1','1902271412torg19116.1','1902221225bogo70833.1','1902041628hovl98595.1','1901221314hjar13390.1','1901211842bogo45450.1','1901211841bogo45278.1','1901211021pors38497.1','1901041518bogo69722.1','1812121629bogo19169.1','1811281316hovl94120.1','1811271548stry35435.1','1811161556sotr98830.1','1811050849hovl29175.1','1810261234bogo51377.1','1810111616stry98043.1','1810091404stry29054.1','1810021409stry73291.1','1810021300stry10035.1','1810011453hovl78721.1','1808281515hovl45154.1','1808281510hovl41157.1','1808281508hovl40027.1','1807041348hovl86853.1','1807021449svan66966.1','1806191152mlyk17867.1','1806131340torg69388.1','1806081638stry15956.1','1806081630stry12005.1','1805291039stry06599.1','1805230915stry12000.1','1805021401komm01057.1','1804031245sotr29227.1','1801101331klos64008.1','1711131512lege39902.1','1710101526lgep99532.1','1710021112lgep84530.1','1709281419torg52203.1','1709150812lgep29931.1','1709010835lgep32074.1','1708180814lgep68603.1','1708141648rotn37304.1','1707281016torg72779.1','1707270921lgep05824.1','1707261052rotn43985.1','1707140933lgep48480.1','1707101547torg79102.1','1707071057mark36492.1','1707031234rotn76227.1','1706271747torg55573.1','1706230836lgep21895.1','1706211308mark68071.1','1706191159norh27862.1','1706151028lgep28909.1','1706121202norh66517.1','1706011019lgep01743.1','1705311255komm47547.1','1705291723etne79654.1','1705241441kiro90617.1','1705241441kiro90605.1','1705181849torg04805.1','1705021853voss08508.1','1704181545sotr41029.1','1704031632lgep65853.1','1703211232mlyk46181.1','1703201928lgep77098.1','1703201821lgep59602.1','1703061414lgep56167.1','1703061301lgep52389.1','1702241659trim41867.1','1702231621trim93212.1','1702201243lgep92782.1','1702201238mlyk86862.1','1702200953lgep02674.1','1702171550fysi20651.1','1702171524fysi00645.1','1702151236fysi74724.1','1702091356lgep03559.1','1702062017lgep95414.1','1702031612fysi90068.1','1701311705fysi68270.1','1701301114sotr33206.1','1701301046sotr03913.1','1701230943lgep41169.1','1701201600fysi43419.1','1701161254fysi57359.1','1701131524fysi38965.1','1701121217inst64086.1','1701090922lgep78837.1','1701061158taul91508.1','1701021942fysi65032.1','1612201600fysi82931.1','1612051811norh58423.1','1612051517fysi44346.1','1611211628kara99773.1','1611160834lgep94612.1','1611021216lone80431.1','1611020825lgep19326.1','1610190745lgep43801.1','1610141240lgep50506.1','1609161358lgep18244.1','1609161212fysi65610.1','1608091611tnsb80090.1','1607291149lgep64182.1','1607221016aurl31484.1','1607220943tnsb16826.1','1607151217lgep30871.1','1606171007lgep12975.1','1602261000lill43373.1')
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
