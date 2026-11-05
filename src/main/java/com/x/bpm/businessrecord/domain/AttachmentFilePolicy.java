package com.x.bpm.businessrecord.domain;

import com.x.bpm.shared.error.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;

/** C11 allowlist. Does not claim malware scanning; no archive is extracted or XML executed. */
public final class AttachmentFilePolicy {
    public static final int MAX_BYTES=20*1024*1024;
    public String validate(String name,byte[] bytes) {
        if(name==null||name.isBlank()||name.length()>255||name.chars().anyMatch(c->c<32||c==127||c=='/'||c=='\\')
                ||bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw invalid();
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".pdf")&&prefix(bytes,"%PDF-".getBytes(StandardCharsets.US_ASCII)))return "application/pdf";
        if(lower.endsWith(".png")&&prefix(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
        if((lower.endsWith(".jpg")||lower.endsWith(".jpeg"))&&prefix(bytes,new byte[]{(byte)255,(byte)216,(byte)255}))return "image/jpeg";
        if(lower.endsWith(".docx")&&office(bytes,"word/document.xml"))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if(lower.endsWith(".xlsx")&&office(bytes,"xl/workbook.xml"))return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        throw invalid();
    }
    private boolean prefix(byte[] bytes,byte[] magic){if(bytes.length<magic.length)return false;for(int i=0;i<magic.length;i++)if(bytes[i]!=magic[i])return false;return true;}
    private boolean office(byte[] bytes,String required) {
        if(!prefix(bytes,new byte[]{80,75,3,4}))return false;
        boolean contentTypes=false,document=false;int count=0;long expanded=0;byte[] buffer=new byte[8192];
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                String name=entry.getName();if(++count>500||name.length()>512||name.startsWith("/")||name.contains("..")||name.contains("\\")||name.toLowerCase(Locale.ROOT).contains("vbaproject"))return false;
                contentTypes|=name.equals("[Content_Types].xml");document|=name.equals(required);
                for(int read=zip.read(buffer);read!=-1;read=zip.read(buffer)){expanded+=read;if(expanded>40L*1024*1024)return false;}
            }
        }catch(IOException invalid){return false;}
        return contentTypes&&document;
    }
    private static ApplicationException invalid(){return new ApplicationException(ErrorCode.VALIDATION_FAILED,"Tệp không hợp lệ. Chỉ nhận PDF/DOCX/XLSX/PNG/JPG, tối đa 20 MB mỗi tệp.");}
}
