package pixy.meta.png;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ListIterator;

import org.w3c.dom.Document;

import pixy.image.png.Chunk;
import pixy.image.png.ChunkType;
import pixy.image.png.TextBuilder;
import pixy.io.IOUtils;
import pixy.string.XMLUtils;

public class PNGMetaManipulator extends PNGMeta {

    
    public static void manipulatePNGMetadata(InputStream is, OutputStream os, String xmpDoc) throws IOException {
        List<Chunk> chunks = readChunks(is);
        ListIterator<Chunk> itr = chunks.listIterator();
        
        // Remove unwanted chunks
        while(itr.hasNext()) {
            Chunk chunk = itr.next();
            // keep all critical chunks
            if (chunk.getChunkType().isCritical())
                continue;
            switch (chunk.getChunkType()){
            case IDAT: // critical
            case IEND: // critical
            case IHDR: // critical
            case PLTE: // critical
            case BKGD: // background
            case CHRM: // chroma
            case GAMA: // gamma
            case ICCP: // ICC profile
            case PHYS: // pixel size and aspect
            case SRGB: // marker for sRGB color space
            case TRNS: // transparency information
                continue; // keep em
            case HIST: // histogram (strip to make smaller)
            case TIME: // last changed
            case SPLT: // suggested palette
            case SBIT: // source bitdepth
            case ITXT: // text
            case TEXT: // text
            case ZTXT: // compressed text
            case UNKNOWN: // don't know, so lets drop it
            default:
                itr.remove(); // drop it!
            }            
        }
        
        // Create XMP textual chunk
        if (xmpDoc!=null){
            Chunk xmpChunk = new TextBuilder(ChunkType.ITXT).keyword("XML:com.adobe.xmp").text(xmpDoc).build();
            // Insert XMP textual chunk into image
            chunks.add(xmpChunk);
        }
        
        IOUtils.writeLongMM(os, SIGNATURE);
        
        serializeChunks(chunks, os);
    }
    

}
