package tr.com.serkanozal.samba;

public interface SambaValueFactory<V> {

    V create();
    void destroy(V value);
    
}
