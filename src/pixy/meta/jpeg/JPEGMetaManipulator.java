package pixy.meta.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pixy.image.jpeg.Marker;
import pixy.image.jpeg.Segment;
import pixy.image.jpeg.UnknownSegment;
import pixy.io.IOUtils;
import pixy.meta.adobe.ImageResourceID;
import pixy.meta.adobe._8BIM;
import pixy.meta.iptc.IPTCDataSet;
import pixy.string.StringUtils;

/**
 * Subclass of JPEGMeta, which was getting very large.
 * 
 * This class adds the more general manipulateJPEGMetadata() function, which can both remove and change metadata
 * with one in-stream operation.
 * 
 * The functionality would have been possible by using the removeMetadata() and insertMetadata() methods already
 * present in JPEGMeta, but would have required multiple passes on the file, storing the intermediate results.
 * 
 * @author Richard Unger
 */
public class JPEGMetaManipulator extends JPEGMeta {

    public final static Logger log = LoggerFactory.getLogger(JPEGMetaManipulator.class);
    
    /**
     * Strips and inserts metadata in one operation.
     * 
     * All existing JPEG metadata elements are removed, except:
     * - basic image information
     * - TODO ICC color profile
     * The new IPTC metadata can be provided in parameter newIptcMetadata.
     * 
     * Note that this method does not close your streams, so remember to have a finally block somewhere that does so!
     * 
     * @param is image input stream
     * @param os result image output stream
     * @param newIptcMetadata new IPTC metadata. May be null if no new metadata should be added/replaced
     * @param metadataTypesToRemove the types of Metadata to strip
     * @throws IOException 
     */
    public static void manipulateJPEGMetadata(InputStream is, OutputStream os, Collection<IPTCDataSet> newIptcMetadata) throws IOException{        
        // The very first marker should be the start_of_image marker!
        if(Marker.fromShort(IOUtils.readShortMM(is)) != Marker.SOI)
            throw new IOException("Invalid JPEG image, expected SOI marker not found!");        
        IOUtils.writeShortMM(os, Marker.SOI.getValue());

        // now read markers and add them to segment buffer if they aren't supposed to be stripped...
        int app0Index = -1;
        List<Segment> segments = new ArrayList<Segment>();
        boolean finished = false;
        while (!finished){
            short marker = IOUtils.readShortMM(is);
            Marker emarker = Marker.fromShort(marker);
                        
            // remember the locations of these markers...
            if (emarker==Marker.APP0) app0Index = segments.size();
            
            switch (emarker){
                case SOS:
                    finished = true; // we've hit the real image data, we're done reading meta segments
                    break;
                case JPG: // JPG and JPGn shouldn't appear in the image.
                case JPG0:
                case JPG13:
                case TEM: // The only stand alone marker besides SOI, EOI, and RSTn.
                    segments.add(new Segment(emarker, 0, null));
                    break;
                case APP1:
                case APP2: // ICC color profile...
                    // TODO check if it actually is a color profile
                    // we want to strip it if it is FXPR block (extended EXIF)
                case APP3:
                case APP4:
                case APP5:
                case APP6:
                case APP7:
                case APP8:
                case APP9:
                case APP10:
                case APP11:
                case APP12:
                case APP13:
                case APP14:
                case APP15:
                case COM:
                    // these blocks get stripped...
                    int skip = IOUtils.readUnsignedShortMM(is);
                    IOUtils.skipFully(is, skip-2);
                    break;
                case APP0: // keep image thumbnail and resolution infos
                default: // everything else gets copied
                    int length = IOUtils.readUnsignedShortMM(is);
                    log.trace("Copying segment "+emarker.name()+"("+StringUtils.intToHexString(emarker.getValue())+") length "+length);
                    byte[] buf = new byte[length - 2];
                    int numRead = IOUtils.read(is, buf);
                    if (numRead!=(length-2))
                        log.warn("Number of bytes read != size field: "+numRead+" != "+(length-2));
                    if(emarker == Marker.UNKNOWN)
                        segments.add(new UnknownSegment(marker, length, buf));
                    else
                        segments.add(new Segment(emarker, length, buf));
            }
        }
        
        // then write the resulting segments out to the output, replacing/inserting the metadata if needed
        // write segments up to APP0 as APP13 has to be inserted afterwards
        for(int i = 0; i <= app0Index; i++)
            segments.get(i).write(os);
        
        // Insert IPTC data as one of IRB 8BIM block
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        for(IPTCDataSet iptc : newIptcMetadata)
            iptc.write(bout);
        // Create 8BIM for IPTC
        _8BIM newBIM = new _8BIM(ImageResourceID.IPTC_NAA.getValue(), "iptc", bout.toByteArray());
        writeIRB(os, newBIM); // Write the one and only one 8BIM as one APP13        
        
        // write remaining segments
        for(int i = (app0Index < 0 ? 0 : app0Index + 1); i < segments.size(); i++)
            segments.get(i).write(os);

        // and copy the rest of the image
        IOUtils.writeShortMM(os, Marker.SOS.getValue());
        copyToEnd(is, os);
    }

    
}
