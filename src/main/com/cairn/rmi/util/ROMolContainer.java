package com.cairn.rmi.util;

import com.cairn.common.RDKitOps;
import org.RDKit.ROMol;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.List;

/**
 *
 * @author Gareth Jones
 */
public class ROMolContainer implements Serializable {

    private static final long serialVersionUID = 1000L;
    private final ROMol mol;

    public ROMolContainer(ROMol mol) {
        this.mol = mol;
    }

    private List<Integer> binary() {
        return RDKitOps.molToBinary(mol);
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
        var pickle = (List<Integer>) in.readObject();
        var molValue = RDKitOps.molFromBinary(pickle);
        try {
            Field molField = getClass().getField("mol");
            if (!molField.canAccess(this))
                molField.setAccessible(true);
            molField.set(this, molValue);
            // don't restore private access to mol field as we may be in a multi-threaded environment
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalStateException("Unable to set mol field", e);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(binary());
    }

    public ROMol getMol() {
        return mol;
    }
}
