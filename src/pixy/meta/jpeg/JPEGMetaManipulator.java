package pixy.meta.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import pixy.image.jpeg.Marker;
import pixy.image.jpeg.Segment;
import pixy.image.jpeg.UnknownSegment;
import pixy.io.IOUtils;
import pixy.meta.MetadataType;
import pixy.meta.iptc.IPTCDataSet;

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

    /**
     * Strips, modifies and replaces metadata in one operation.
     * 
     * The metadata to strip is provided in parameter metadataTypesToRemove.
     * The new IPTC metadata can be provided in parameter newIptcMetadata.
     * 
     * Adding/replacing/stripping works according to the following logic:
     * If no new data is provided and IPTC is not stripped, the metadata is copied unchanged.
     * If new data is provided and IPTC is not stripped, the new metadata is added to the old.
     * If new data is provided and IPTC is stripped, the new metadata replaces the old.
     * If no new data is provided and IPTC is stripped, the new file will have no IPTC data.
     * 
     * Note that this method does not close your streams, so remember to have a finally block somewhere that does so!
     * 
     * @param is image input stream
     * @param os result image output stream
     * @param newIptcMetadata new IPTC metadata. May be null if no new metadata should be added/replaced
     * @param metadataTypesToRemove the types of Metadata to strip
     * @throws IOException 
     */
    public static void manipulateJPEGMetadata(InputStream is, OutputStream os, Collection<IPTCDataSet> newIptcMetadata, MetadataType ... metadataTypesToRemove) throws IOException{
        EnumSet<MetadataType> toRemove;
        if (metadataTypesToRemove.length<1) 
            toRemove = EnumSet.noneOf(MetadataType.class);
        else
            toRemove = EnumSet.copyOf(Arrays.asList(metadataTypesToRemove));
        
        // The very first marker should be the start_of_image marker!
        if(Marker.fromShort(IOUtils.readShortMM(is)) != Marker.SOI)
            throw new IOException("Invalid JPEG image, expected SOI marker not found!");        
        IOUtils.writeShortMM(os, Marker.SOI.getValue());

        // now read markers and add them to segment buffer if they aren't supposed to be stripped...
        int app0Index = -1, app1Index = -1;
        List<Segment> segments = new ArrayList<Segment>();
        boolean finished = false;
        while (!finished){
            short marker = IOUtils.readShortMM(is);
            Marker emarker = Marker.fromShort(marker);
            
            // strip metadata according to metadataTypesToRemove
            // TODO need to skip over the segments as well...
            if (emarker==Marker.APP0 && toRemove.contains(MetadataType.JPG_JFIF)) continue;
            if (emarker==Marker.APP1 && toRemove.contains(MetadataType.EXIF)) continue;
            if (emarker==Marker.APP1 && toRemove.contains(MetadataType.XMP)) continue; // TODO handle XMP vs. EXIF in APP1
            if (emarker==Marker.APP2 && toRemove.contains(MetadataType.ICC_PROFILE)) continue;
            if (emarker==Marker.APP12 && toRemove.contains(MetadataType.JPG_DUCKY)) continue;
            if (emarker==Marker.APP13 && toRemove.contains(MetadataType.IPTC)) continue; // TODO handle IPTC vs. IRB
            if (emarker==Marker.APP13 && toRemove.contains(MetadataType.PHOTOSHOP_IRB)) continue;
            if (emarker==Marker.APP14 && toRemove.contains(MetadataType.JPG_ADOBE)) continue;
            if (emarker==Marker.COM && toRemove.contains(MetadataType.COMMENT)) continue;
            
            // remember the locations of these markers...
            if (emarker==Marker.APP0) app0Index = segments.size();
            if (emarker==Marker.APP1) app1Index = segments.size();
            
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
                case APP13:
//                    if(eightBIMStream == null)
//                        eightBIMStream = new ByteArrayOutputStream();
//                    readAPP13(is, eightBIMStream);
                    break;
                default:
                    int length = IOUtils.readUnsignedShortMM(is);                   
                    byte[] buf = new byte[length - 2];
                    IOUtils.readFully(is, buf);
                    if(emarker == Marker.UNKNOWN)
                        segments.add(new UnknownSegment(marker, length, buf));
                    else
                        segments.add(new Segment(emarker, length, buf));
            }
        }
        
        // then write the resulting segments out to the output, replacing/inserting the metadata if needed
        int index = Math.max(app0Index, app1Index);
        // write segments up to APP0 or APP1, as APP13 has to be inserted afterwards
        for(int i = 0; i <= index; i++)
            segments.get(i).write(os);
        
        // TODO insert APP13, if needed
        
        // write remaining segments
        for(int i = (index < 0 ? 0 : index + 1); i < segments.size(); i++)
            segments.get(i).write(os);

        // and copy the rest of the image
        IOUtils.writeShortMM(os, Marker.SOS.getValue());
        copyToEnd(is, os);
    }

    
}
