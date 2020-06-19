/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the files COPYING and Copyright.html. *
 * COPYING can be found at the root of the source code distribution tree.    *
 * Or, see https://support.hdfgroup.org/products/licenses.html               *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package hdf.view.dialog;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.h5.H5Datatype;
import hdf.object.h5.H5File;
import hdf.view.Tools;
import hdf.view.ViewProperties;

/**
 * NewDataDialog is an intermediate class for creating data types.
 */
public class NewDataObjectDialog extends Dialog {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NewDataObjectDialog.class);

    protected Shell   shell;

    protected Font    curFont;

    /** the object which the this object is attached */
    protected HObject parentObj;

    /** the object referenced */
    protected HObject refObject;

    /** the object created */
    protected HObject newObject;

    /** TextField for entering the length of the data array or string. */
    protected Text    lengthField;

    /** The Choice of the datatypes */
    protected Combo   namedChoice;
    protected Combo   classChoice;
    protected Combo   sizeChoice;
    protected Combo   endianChoice;

    /** The Choice of the object list */
    protected Button  useCommittedType;
    protected Button  checkUnsigned;
    protected List<?> objList;
    protected List<Datatype> namedList;
    protected Label   arrayLengthLabel;

    /** The attributes of the datatype */
    public int tclass = Datatype.CLASS_NO_CLASS;
    public int tsize = Datatype.NATIVE;
    public int torder = Datatype.NATIVE;
    public int tsign = Datatype.NATIVE;
    public boolean isEnum = false;
    public String strEnumMap = null;
    public boolean isVLen = false;
    public boolean isVlenStr = false;

    protected FileFormat fileFormat;

    protected boolean isH5;

    public NewDataObjectDialog(Shell parent, HObject pGroup, List<?> objs) {
        super(parent, SWT.APPLICATION_MODAL);

        try {
            curFont = new Font(
                    Display.getCurrent(),
                    ViewProperties.getFontType(),
                    ViewProperties.getFontSize(),
                    SWT.NORMAL);
        }
        catch (Exception ex) {
            curFont = null;
        }

        newObject = null;
        parentObj = pGroup;
        objList = objs;

        fileFormat = pGroup.getFileFormat();
        isH5 = pGroup.getFileFormat().isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5));
    }

    /** @return the new dataset created. */
    public void createDatatypeWidget() {
        Label label;

        // Create Datatype region
        org.eclipse.swt.widgets.Group datatypeGroup = new org.eclipse.swt.widgets.Group(shell, SWT.NONE);
        datatypeGroup.setFont(curFont);
        datatypeGroup.setText("Datatype");
        datatypeGroup.setLayout(new GridLayout(4, true));
        datatypeGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        useCommittedType = new Button(datatypeGroup, SWT.CHECK);
        useCommittedType.setFont(curFont);
        useCommittedType.setText("Use Committed Datatype");
        useCommittedType.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
        useCommittedType.setEnabled(isH5);

        if(isH5) {
            label = new Label(datatypeGroup, SWT.LEFT);
            label.setFont(curFont);
            label.setText("Committed Datatype: ");

            namedChoice = new Combo(datatypeGroup, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
            namedChoice.setFont(curFont);
            namedChoice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
            namedChoice.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    refObject = namedList.get(namedChoice.getSelectionIndex());
                }
            });

            //Dummy label
            label = new Label(datatypeGroup, SWT.LEFT);
            label.setFont(curFont);
            label.setText("");

            namedList = new Vector<>(objList.size());
            Object obj = null;
            Iterator<?> iterator = objList.iterator();
            while (iterator.hasNext()) {
                obj = iterator.next();
                if (obj instanceof Datatype) {
                    H5Datatype ndt = (H5Datatype) obj;
                    namedList.add(ndt);
                    namedChoice.add(((Datatype)obj).getFullName());
                }
            }

            if (refObject != null)
                namedChoice.select(namedChoice.indexOf(((Datatype)refObject).getFullName()));
        }

        label = new Label(datatypeGroup, SWT.LEFT);
        label.setFont(curFont);
        label.setText("Datatype Class");

        label = new Label(datatypeGroup, SWT.LEFT);
        label.setFont(curFont);
        label.setText("Size (bits)");

        label = new Label(datatypeGroup, SWT.LEFT);
        label.setFont(curFont);
        label.setText("Byte Ordering");

        checkUnsigned = new Button(datatypeGroup, SWT.CHECK);
        checkUnsigned.setFont(curFont);
        checkUnsigned.setText("Unsigned");
        checkUnsigned.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));

        classChoice = new Combo(datatypeGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        classChoice.setFont(curFont);
        classChoice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        classChoice.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int idx = classChoice.getSelectionIndex();
                sizeChoice.select(0);
                endianChoice.select(0);
                lengthField.setEnabled(false);

                if ((idx == 0) || (idx == 6)) { // INTEGER
                    sizeChoice.setEnabled(true);
                    endianChoice.setEnabled(isH5);
                    checkUnsigned.setEnabled(true);

                    if (sizeChoice.getItemCount() == 3) {
                        sizeChoice.remove("32");
                        sizeChoice.remove("64");
                        sizeChoice.add("8");
                        sizeChoice.add("16");
                        sizeChoice.add("32");
                        sizeChoice.add("64");
                    }
                }
                else if ((idx == 1) || (idx == 7)) { // FLOAT
                    sizeChoice.setEnabled(true);
                    endianChoice.setEnabled(isH5);
                    checkUnsigned.setEnabled(false);

                    if (sizeChoice.getItemCount() == 5) {
                        sizeChoice.remove("16");
                        sizeChoice.remove("8");
                    }
                }
                else if (idx == 2) { // CHAR
                    sizeChoice.setEnabled(false);
                    endianChoice.setEnabled(isH5);
                    checkUnsigned.setEnabled(true);
                }
                else if (idx == 3) { // STRING
                    sizeChoice.setEnabled(false);
                    endianChoice.setEnabled(false);
                    checkUnsigned.setEnabled(false);
                    lengthField.setEnabled(true);
                    lengthField.setText("String length");
                }
                else if (idx == 4) { // REFERENCE
                    sizeChoice.setEnabled(false);
                    endianChoice.setEnabled(false);
                    checkUnsigned.setEnabled(false);
                    lengthField.setEnabled(false);
                }
                else if (idx == 5) { // ENUM
                    sizeChoice.setEnabled(true);
                    checkUnsigned.setEnabled(true);
                    lengthField.setEnabled(true);
                    lengthField.setText("0=R,1=G,#=TXT,...");
                }
                else if (idx == 8) {
                    sizeChoice.setEnabled(false);
                    endianChoice.setEnabled(false);
                    checkUnsigned.setEnabled(false);
                    lengthField.setEnabled(false);
                }
            }
        });

        classChoice.add("INTEGER");
        classChoice.add("FLOAT");
        classChoice.add("CHAR");

        if(isH5) {
            classChoice.add("STRING");
            classChoice.add("REFERENCE");
            classChoice.add("ENUM");
            classChoice.add("VLEN_INTEGER");
            classChoice.add("VLEN_FLOAT");
            classChoice.add("VLEN_STRING");
        }

        sizeChoice = new Combo(datatypeGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        sizeChoice.setFont(curFont);
        sizeChoice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        sizeChoice.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (classChoice.getSelectionIndex() == 0) {
                    checkUnsigned.setEnabled(true);
                }
            }
        });

        if(isH5) {
            sizeChoice.add("NATIVE");
        }
        else {
            sizeChoice.add("DEFAULT");
        }

        sizeChoice.add("8");
        sizeChoice.add("16");
        sizeChoice.add("32");
        sizeChoice.add("64");

        endianChoice = new Combo(datatypeGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        endianChoice.setFont(curFont);
        endianChoice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        endianChoice.setEnabled(isH5);

        if(isH5) {
            endianChoice.add("NATIVE");
            endianChoice.add("LITTLE ENDIAN");
            endianChoice.add("BIG ENDIAN");
        }
        else {
            endianChoice.add("DEFAULT");
        }

        lengthField = new Text(datatypeGroup, SWT.SINGLE | SWT.BORDER);
        lengthField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        lengthField.setFont(curFont);
        lengthField.setText("String Length");
        lengthField.setEnabled(false);

        classChoice.select(0);
        sizeChoice.select(0);
        endianChoice.select(0);

        if (useCommittedType.getSelection()) {
            classChoice.setEnabled(false);
            sizeChoice.setEnabled(false);
            endianChoice.setEnabled(false);
            checkUnsigned.setEnabled(false);
        }
        else {
            refObject = null;
            classChoice.setEnabled(true);
            sizeChoice.setEnabled(true);
            endianChoice.setEnabled(isH5);
            checkUnsigned.setEnabled(true);
        }
    }

    public Datatype createNewDatatype(String name) {
        Datatype datatype = null;

        tclass = Datatype.CLASS_NO_CLASS;
        tsize = Datatype.NATIVE;
        torder = Datatype.NATIVE;
        tsign = Datatype.NATIVE;
        isEnum = false;
        strEnumMap = null;
        isVLen = false;
        isVlenStr = false;

        log.trace("start");

        if (useCommittedType.getSelection()) {
            datatype = (Datatype)refObject;
        }
        else {
           // set datatype class
            int idx = classChoice.getSelectionIndex();
            if (idx == 0) {
                tclass = Datatype.CLASS_INTEGER;
                if (checkUnsigned.getSelection()) {
                    tsign = Datatype.SIGN_NONE;
                }
            }
            else if (idx == 1) {
                tclass = Datatype.CLASS_FLOAT;
            }
            else if (idx == 2) {
                tclass = Datatype.CLASS_CHAR;
                if (checkUnsigned.getSelection()) {
                    tsign = Datatype.SIGN_NONE;
                }
            }
            else if (idx == 3) {
                tclass = Datatype.CLASS_STRING;
            }
            else if (idx == 4) {
                tclass = Datatype.CLASS_REFERENCE;
            }
            else if (idx == 5) {
                isEnum = true;
                tclass = Datatype.CLASS_INTEGER;
            }
            else if (idx == 6) {
                isVLen = true;
                tclass = Datatype.CLASS_INTEGER;
                if (checkUnsigned.getSelection()) {
                    tsign = Datatype.SIGN_NONE;
                }
            }
            else if (idx == 7) {
                isVLen = true;
                tclass = Datatype.CLASS_FLOAT;
            }
            else if (idx == 8) {
                tclass = Datatype.CLASS_STRING;
                isVlenStr = true;
                tsize = -1;
            }

            // set datatype size/order
            idx = sizeChoice.getSelectionIndex();
            if (tclass == Datatype.CLASS_STRING) {
                log.trace("CLASS_STRING start");
                if (!isVlenStr) {
                    int stringLength = 0;
                    try {
                        stringLength = Integer.parseInt(lengthField.getText());
                    }
                    catch (NumberFormatException ex) {
                        stringLength = -1;
                    }

                    if (stringLength <= 0) {
                        shell.getDisplay().beep();
                        Tools.showError(shell, "Create", "Invalid string length: " + lengthField.getText());
                        return null;
                    }
                    tsize = stringLength;
                }
            }
            else if (tclass == Datatype.CLASS_REFERENCE) {
                tsize = 1;
                torder = Datatype.NATIVE;
            }
            else if (idx == 0) {
                tsize = Datatype.NATIVE;
            }
            else if (tclass == Datatype.CLASS_FLOAT) {
                tsize = idx * 4;
            }
            else if (tclass == Datatype.CLASS_INTEGER) {
                switch(idx) {
                    case 0:
                        tsize = 1;
                        break;
                    case 1:
                        tsize = 2;
                        break;
                    case 2:
                        tsize = 4;
                        break;
                    case 3:
                        tsize = 8;
                        break;
                    default:
                        break;
                }
                log.trace("CLASS_INTEGER: tsize={}", tsize);
            }
            else if (tclass == Datatype.CLASS_FLOAT) {
                tsize = (idx + 1) * 4;
                log.trace("CLASS_FLOAT: tsize={}", tsize);
            }
            else {
                tsize = 1 << (idx - 1);
            }

            if ((tsize == 8) && !isH5 && (tclass == Datatype.CLASS_INTEGER)) {
                shell.getDisplay().beep();
                Tools.showError(shell, "Create", "HDF4 does not support 64-bit integer.");
                return null;
            }

            // set order
            idx = endianChoice.getSelectionIndex();
            if (idx == 0) {
                torder = Datatype.NATIVE;
            }
            else if (idx == 1) {
                torder = Datatype.ORDER_LE;
            }
            else {
                torder = Datatype.ORDER_BE;
            }

            Datatype basedatatype = null;
            try {
                if (isVLen) {
                    log.trace("create VLen base type");
                    basedatatype = fileFormat.createDatatype(tclass, tsize, torder, tsign);
                    tclass = Datatype.CLASS_VLEN;
                }
                if (isEnum && isH5) {
                    log.trace("create Enum base type");
                    basedatatype = fileFormat.createDatatype(tclass, tsize, torder, tsign);

                    strEnumMap = lengthField.getText();
                    if ((strEnumMap == null) || (strEnumMap.length() < 1) || strEnumMap.endsWith("...")) {
                        shell.getDisplay().beep();
                        Tools.showError(shell, "Create", "Invalid member values: " + lengthField.getText());
                        return null;
                    }
                    log.trace("CLASS_ENUM enumStr={}", strEnumMap);

                    tclass = Datatype.CLASS_ENUM;
                    Datatype t = fileFormat.createDatatype(tclass, tsize, torder, tsign, basedatatype);
                    return ((H5File)fileFormat).createNamedDatatype(t, strEnumMap, name);
                }
                else {
                    datatype = fileFormat.createNamedDatatype(tclass, tsize, torder, tsign, basedatatype, name);
                }
            }
            catch (Exception ex) {
                shell.getDisplay().beep();
                Tools.showError(shell, "Create", ex.getMessage());
                return null;
            }
        }
        log.trace("finish");
        return datatype;
    }

    /** @return the new object created. */
    public HObject getObject() {
        return newObject;
    }

    /** @return the parent group of the new dataset. */
    public Group getParentGroup() {
        return (Group) parentObj;
    }
}
