package pt.sonora.offline;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, temporary content grants; never exposes arbitrary paths. */
public class VideoProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri) throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if(name==null||!name.matches("sonora-[0-9]+\\.mp4"))throw new FileNotFoundException();
        File f=new File(getContext().getFilesDir(),name);if(!f.isFile())throw new FileNotFoundException();return f;
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Apenas leitura");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public String getType(Uri uri){return "video/mp4";}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=file(uri);String[] cols=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};MatrixCursor cursor=new MatrixCursor(cols);Object[] values=new Object[cols.length];
            for(int i=0;i<cols.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;
            cursor.addRow(values);return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
