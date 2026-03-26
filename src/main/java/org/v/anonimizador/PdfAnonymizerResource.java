package org.v.anonimizador;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.*;

@Path("/anonimizador")
public class PdfAnonymizerResource {

    @POST
    @Path("/redact")
    @Consumes({MediaType.APPLICATION_OCTET_STREAM, "application/pdf"})
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response redact(@QueryParam("safe") @DefaultValue("true") boolean safe,
                           InputStream uploadedPdf) {
        try {
            byte[] result = PdfRedactor.redact(uploadedPdf, safe);
            return Response.ok(new ByteArrayInputStream(result))
                    .header("Content-Disposition", "attachment; filename=\"anonimizado.pdf\"")
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao anonimizar: " + e.getMessage())
                    .build();
        }
    }
}