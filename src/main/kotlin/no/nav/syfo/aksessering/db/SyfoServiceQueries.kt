package no.nav.syfo.aksessering.db

import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.SyfoserviceSykmelding

fun DatabaseInterface.hentSykmeldinger(): List<SyfoserviceSykmelding> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                SELECT partner.partner_id
                FROM PARTNER partner, ABONNEMENT abonnement
                WHERE partner.PARTNER_ID = abonnement.PARTNER_ID
                AND abonnement.tjeneste_id = '3'
                AND (abonnement.SLUTT_DATO > sysdate or abonnement.SLUTT_DATO is NULL)
                AND partner.her_id=?
                """
            ).use {
                it.executeQuery().toList { toElektroniskAbonoment() }
            }
        }

fun ResultSet.toElektroniskAbonoment(): SyfoserviceSykmelding =
        SyfoserviceSykmelding(
                getString("partner_id")
        )
