package com.x.bpm.businessrecord.domain;

import com.x.bpm.shared.error.ApplicationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AttachmentFilePolicyTest {
    private final AttachmentFilePolicy policy=new AttachmentFilePolicy();
    @Test void checksSignaturesAndOfficeContainersInsteadOfClientMimeType()throws Exception{
        assertThat(policy.validate("Chứng từ.PDF","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
        assertThat(policy.validate("photo.PNG",new byte[]{(byte)137,80,78,71,13,10,26,10})).isEqualTo("image/png");
        assertThat(policy.validate("photo.jpeg",new byte[]{(byte)255,(byte)216,(byte)255,1})).isEqualTo("image/jpeg");
        assertThat(policy.validate("receipt.docx",office("word/document.xml"))).contains("wordprocessingml");
        assertThat(policy.validate("receipt.xlsx",office("xl/workbook.xml"))).contains("spreadsheetml");
    }
    @Test void rejectsTraversalSpoofingEmptyOversizeAndDangerousOfficeEntries()throws Exception{
        byte[] pdf="%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        for(String name:new String[]{"../receipt.pdf","folder\\receipt.pdf","file\r\nX-Header: value.pdf","file.exe","receipt.html"})assertThatThrownBy(()->policy.validate(name,pdf)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->policy.validate("receipt.pdf","<script>bad</script>".getBytes(StandardCharsets.US_ASCII))).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->policy.validate("receipt.pdf",new byte[0])).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->policy.validate("receipt.pdf",new byte[AttachmentFilePolicy.MAX_BYTES+1])).isInstanceOf(ApplicationException.class);
        for(String entry:new String[]{"../word/document.xml","word/vbaProject.bin","xl/workbook.xml"})assertThatThrownBy(()->policy.validate("receipt.docx",office(entry))).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->policy.validate("receipt.docx",office("word/document.xml","word/vbaProject.bin"))).isInstanceOf(ApplicationException.class);
    }
    private byte[] office(String... entries)throws Exception{var output=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){for(String name:java.util.stream.Stream.concat(java.util.stream.Stream.of("[Content_Types].xml"),java.util.Arrays.stream(entries)).toList()){zip.putNextEntry(new ZipEntry(name));zip.write("<root/>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return output.toByteArray();}
}
