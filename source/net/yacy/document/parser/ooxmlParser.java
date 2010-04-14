//odtParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.File;
import java.io.InputStream;
import java.io.Writer;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.document.parser.xml.ODContentHandler;
import net.yacy.document.parser.xml.ODMetaHandler;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;



public class ooxmlParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("docx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        SUPPORTED_EXTENSIONS.add("dotx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.wordprocessingml.template");
        SUPPORTED_EXTENSIONS.add("potx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.template");
        SUPPORTED_EXTENSIONS.add("ppsx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        SUPPORTED_EXTENSIONS.add("pptx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        SUPPORTED_EXTENSIONS.add("xlsx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        SUPPORTED_EXTENSIONS.add("xltx");
        SUPPORTED_MIME_TYPES.add("application/vnd.openxmlformats-officedocument.spreadsheetml.template");
    }

    public ooxmlParser() {        
        super("Open Office XML Document Parser"); 
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    @Override
    public Document parse(final DigestURI location, final String mimeType, final String charset, final File dest) throws ParserException, InterruptedException {
        
        Writer writer = null;
        File writerFile = null;
        try {          
            String docDescription = null;
            String docKeywordStr  = null;
            String docShortTitle  = null;
            String docLongTitle   = null;
            String docAuthor      = null;
            String docLanguage    = null;
            
            // opening the file as zip file
            final ZipFile zipFile= new ZipFile(dest);
            final Enumeration<? extends ZipEntry> zipEnum = zipFile.entries();
            final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            
            // looping through all containing files
            while (zipEnum.hasMoreElements()) {
                // check for interruption
                checkInterruption();
                
                // getting the next zip file entry
                final ZipEntry zipEntry= zipEnum.nextElement();
                final String entryName = zipEntry.getName();
                
                // content.xml contains the document content in xml format
                if (entryName.equals("word/document.xml")
                	|| entryName.startsWith("ppt/slides/slide")
                	|| entryName.startsWith("xl/worksheets/sheet")) {
                    
                    // create a writer for output
                    writer = new CharBuffer();
                    
                    // extract data
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    final SAXParser saxParser = saxParserFactory.newSAXParser();
                    saxParser.parse(zipFileEntryStream, new ODContentHandler(writer));
                
                    // close readers and writers
                    zipFileEntryStream.close();
                    writer.close();
                    
                } else if (entryName.equals("docProps/core.xml")) {
                    //  meta.xml contains metadata about the document
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    final SAXParser saxParser = saxParserFactory.newSAXParser();
                    final ODMetaHandler metaData = new ODMetaHandler();
                    saxParser.parse(zipFileEntryStream, metaData);
                    docDescription = metaData.getDescription();
                    docKeywordStr  = metaData.getKeyword();
                    docShortTitle  = metaData.getTitle();
                    docLongTitle   = metaData.getSubject();
                    docAuthor      = metaData.getCreator();
                    docLanguage    = metaData.getLanguage();
                }
            }
            
            // make the languages set
            Set<String> languages = new HashSet<String>(1);
            if (docLanguage != null && docLanguage.length() == 0)
        	languages.add(docLanguage);
            
            // if there is no title availabe we generate one
            if (docLongTitle == null || docLongTitle.length() == 0) {
                if (docShortTitle != null) {
                    docLongTitle = docShortTitle;
                } 
            }            
         
            // split the keywords
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");
            
            // create the parser document
            Document theDoc = null;
            if (writer instanceof CharBuffer) {
                final byte[] contentBytes = ((CharBuffer)writer).toString().getBytes("UTF-8");
                theDoc = new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        languages,
                        docKeywords,
                        docLongTitle,
                        docAuthor,
                        null,
                        docDescription,
                        contentBytes,
                        null,
                        null,
                        false);
            } else {
                theDoc = new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        languages,
                        docKeywords,
                        docLongTitle,
                        docAuthor,
                        null,
                        docDescription,
                        writerFile,
                        null,
                        null,
                        false);
            }
            return theDoc;
        } catch (final Exception e) {            
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            // close the writer
            if (writer != null) try { writer.close(); } catch (final Exception ex) {/* ignore this */}
            
            // delete the file
            if (writerFile != null) FileUtils.deletedelete(writerFile);
            Log.logException(e);
            throw new ParserException("Unexpected error while parsing odt file. " + e.getMessage(),location); 
        }
    }
    
    public Document parse(final DigestURI location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        File dest = null;
        try {
            // creating a tempfile
            dest = File.createTempFile("OpenDocument", ".odt");
            dest.deleteOnExit();
            
            // copying the stream into a file
            FileUtils.copy(source, dest);
            
            // parsing the content
            return parse(location, mimeType, charset, dest);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing odt file. " + e.getMessage(),location); 
        } finally {
            if (dest != null) FileUtils.deletedelete(dest);
        }
    }
    
    @Override
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
}
