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

package hdf.view.TableView;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.data.validate.DataValidator;
import org.eclipse.nebula.widgets.nattable.data.validate.ValidationFailedException;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;

import hdf.object.CompoundDataFormat;
import hdf.object.DataFormat;
import hdf.object.Datatype;

/**
 * A Factory class to return a DataValidator class for a NatTable instance based
 * upon the Datatype that it is supplied.
 *
 * @author Jordan T. Henderson
 * @version 1.0 6/28/2018
 *
 */
public class DataValidatorFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataValidatorFactory.class);

    /*
     * To keep things clean from an API perspective, keep a static reference to the
     * last CompoundDataFormat that was passed in. This keeps us from needing to
     * pass the CompoundDataFormat object as a parameter to every DataValidator
     * class, since it's really only needed by the CompoundDataValidator.
     */
    private static DataFormat dataFormatReference = null;

    public static HDFDataValidator getDataValidator(final DataFormat dataObject) throws Exception {
        log.trace("getDataValidator(DataFormat): start");

        if (dataObject == null) {
            log.debug("getDataValidator(DataFormat): data object is null");
            throw new Exception("Must supply a valid DataFormat to the DataValidatorFactory");
        }

        dataFormatReference = dataObject;

        HDFDataValidator validator = null;
        try {
            validator = getDataValidator(dataObject.getDatatype());
        }
        catch (Exception ex) {
            log.debug("getDataValidator(DataFormat): failed to retrieve a DataValidator: ", ex);
            validator = null;
        }

        /*
         * By default, never validate if a proper DataValidator was not found.
         */
        if (validator == null) {
            log.debug("getDataValidator(DataFormat): using a default data validator");

            validator = new HDFDataValidator(dataObject.getDatatype());
        }

        log.trace("getDataValidator(DataFormat): finish");

        return validator;
    }

    private static HDFDataValidator getDataValidator(Datatype dtype) throws Exception {
        log.trace("getDataValidator(Datatype): start");

        HDFDataValidator validator = null;

        try {
            if (dtype.isCompound())
                validator = new CompoundDataValidator(dtype);
            else if (dtype.isArray())
                validator = new ArrayDataValidator(dtype);
            else if (dtype.isVLEN() && !dtype.isVarStr())
                validator = new VlenDataValidator(dtype);
            else if (dtype.isString() || dtype.isVarStr())
                validator = new StringDataValidator(dtype);
            else if (dtype.isChar())
                validator = new CharDataValidator(dtype);
            else if (dtype.isInteger() || dtype.isFloat())
                validator = new NumericalDataValidator(dtype);
            else if (dtype.isEnum())
                validator = new EnumDataValidator(dtype);
            else if (dtype.isOpaque() || dtype.isBitField())
                validator = new BitfieldDataValidator(dtype);
            else if (dtype.isRef())
                validator = new RefDataValidator(dtype);
        }
        catch (Exception ex) {
            log.debug("getDataValidator(Datatype): failed to retrieve a DataValidator: ", ex);
            validator = null;
        }

        /*
         * By default, never validate if a proper DataValidator was not found.
         */
        if (validator == null) {
            log.debug("getDataValidator(Datatype): using a default data validator");

            validator = new HDFDataValidator(dtype);
        }

        log.trace("getDataValidator(Datatype): finish");

        return validator;
    }

    public static class HDFDataValidator extends DataValidator {

        protected org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HDFDataValidator.class);

        /*
         * This field is only used for CompoundDataValidator, but when the top-level
         * DataValidator is a "container" type, such as an ArrayDataValidator, we have
         * to set this field and pass it through in case there is a
         * CompoundDataValidator at the bottom of the chain.
         */
        protected int cellColIdx;

        HDFDataValidator(final Datatype dtype) {
            log.trace("constructor: start");

            cellColIdx = -1;

            log.trace("constructor: finish");
        }

        @Override
        public boolean validate(ILayerCell cell, IConfigRegistry configRegistry, Object newValue) {
            throwValidationFailedException(cell.getRowIndex(), cell.getColumnIndex(), newValue,
                    "A proper DataValidator wasn't found for this type of data. Writing this type of data will be disabled.");

            return false;
        }

        @Override
        public boolean validate(int colIndex, int rowIndex, Object newValue) {
            throwValidationFailedException(rowIndex, colIndex, newValue,
                    "A proper DataValidator wasn't found for this type of data. Writing this type of data will be disabled.");

            return false;
        }

        protected void checkValidValue(Object newValue) throws ValidationFailedException {
            if (newValue == null)
                throw new ValidationFailedException("value is null");

            if (!(newValue instanceof String))
                throw new ValidationFailedException("value is not a String");
        }

        protected void throwValidationFailedException(int rowIndex, int colIndex, Object newValue, String reason)
                throws ValidationFailedException {
            throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                    + colIndex + ") to '" + newValue.toString() + "': " + reason);
        }

    }

    /*
     * NatTable DataValidator to validate entered input for a dataset with
     * a Compound datatype by calling the appropriate validator on the member
     * at the given row and column index. The correct validator is determined
     * by taking the column index modulo the number of selected members in the
     * Compound datatype, and grabbing the correct validator from the stored
     * list of validators.
     */
    private static class CompoundDataValidator extends HDFDataValidator {

        private final HashMap<Integer, Integer> baseValidatorIndexMap;
        private final HashMap<Integer, Integer> relCmpdStartIndexMap;

        private final HDFDataValidator[]        memberValidators;

        private final int                       nTotFields;

        CompoundDataValidator(final Datatype dtype) throws Exception {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(CompoundDataValidator.class);

            log.trace("constructor: start");

            if (!dtype.isCompound()) {
                log.debug("datatype is not a compound type");
                log.trace("constructor: finish");
                throw new Exception("CompoundDataValidator: datatype is not a compound type");
            }

            CompoundDataFormat compoundFormat = (CompoundDataFormat) dataFormatReference;

            List<Datatype> allSelectedMemberTypes = Arrays.asList(compoundFormat.getSelectedMemberTypes());
            if (allSelectedMemberTypes == null) {
                log.debug("selected compound member datatype list is null");
                throw new Exception("CompoundDataValidator: selected compound member datatype list is null");
            }

            List<Datatype> localSelectedTypes = DataFactoryUtils.filterNonSelectedMembers(allSelectedMemberTypes, dtype);

            log.trace("setting up {} base HDFDataValidators", localSelectedTypes.size());

            memberValidators = new HDFDataValidator[localSelectedTypes.size()];
            for (int i = 0; i < memberValidators.length; i++) {
                log.trace("retrieving DataValidator for member {}", i);

                try {
                    memberValidators[i] = getDataValidator(localSelectedTypes.get(i));
                }
                catch (Exception ex) {
                    log.debug("failed to retrieve DataValidator for member {}: ", i, ex);
                    memberValidators[i] = null;
                }
            }

            /*
             * Build necessary index maps.
             */
            HashMap<Integer, Integer>[] maps = DataFactoryUtils.buildIndexMaps(allSelectedMemberTypes, localSelectedTypes);
            baseValidatorIndexMap = maps[DataFactoryUtils.COL_TO_BASE_CLASS_MAP_INDEX];
            relCmpdStartIndexMap = maps[DataFactoryUtils.CMPD_START_IDX_MAP_INDEX];

            log.trace("index maps built: baseValidatorIndexMap = {}, relColIdxMap = {}",
                    baseValidatorIndexMap.toString(), relCmpdStartIndexMap.toString());

            nTotFields = baseValidatorIndexMap.size();
            if (nTotFields == 0) {
                log.debug("index mapping is invalid - size 0");
                throw new Exception("CompoundDataValidator: invalid mapping of size 0 built");
            }

            log.trace("constructor: finish");
        }

        @Override
        public boolean validate(ILayerCell cell, IConfigRegistry configRegistry, Object newValue) {
            cellColIdx = cell.getColumnIndex() % nTotFields;
            return validate(cell.getColumnIndex(), cell.getRowIndex(), newValue);
        }

        @Override
        public boolean validate(int colIndex, int rowIndex, Object newValue) {
            log.trace("validate({}, {}, {}): start", rowIndex, colIndex, newValue);

            try {
                super.checkValidValue(newValue);

                if (cellColIdx >= nTotFields)
                    cellColIdx %= nTotFields;

                HDFDataValidator validator = memberValidators[baseValidatorIndexMap.get(cellColIdx)];
                validator.cellColIdx = cellColIdx - relCmpdStartIndexMap.get(cellColIdx);

                validator.validate(colIndex, rowIndex, newValue);
            }
            catch (Exception ex) {
                log.debug("validate({}, {}, {}): failed to validate: ", rowIndex, colIndex, newValue, ex);
                throw new ValidationFailedException(ex.getMessage(), ex);
            }
            finally {
                log.trace("validate({}, {}, {}): finish", rowIndex, colIndex, newValue);
            }

            return true;
        }

    }

    /*
     * NatTable DataValidator to validate entered input for a dataset with
     * an ARRAY datatype by calling the appropriate validator (as determined
     * by the supplied datatype) on each of the array's elements.
     */
    private static class ArrayDataValidator extends HDFDataValidator {

        private final HDFDataValidator baseValidator;

        ArrayDataValidator(final Datatype dtype) throws Exception {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(ArrayDataValidator.class);

            log.trace("constructor: start");

            if (!dtype.isArray()) {
                log.debug("datatype is not an array type");
                log.trace("constructor: finish");
                throw new Exception("ArrayDataValidator: datatype is not an array type");
            }

            Datatype baseType = dtype.getDatatypeBase();
            if (baseType == null) {
                log.debug("base datatype is null");
                log.trace("constructor: finish");
                throw new Exception("ArrayDataValidator: base datatype is null");
            }

            log.trace("ArrayDataValidator: base Datatype is {}", baseType.getDescription());

            try {
                baseValidator = getDataValidator(baseType);
            }
            catch (Exception ex) {
                log.debug("couldn't get DataValidator for base datatype: ", ex);
                log.trace("constructor: finish");
                throw new Exception("ArrayDataValidator: couldn't get DataValidator for base datatype: " + ex.getMessage());
            }

            log.trace("constructor: finish");
        }

        @Override
        public boolean validate(ILayerCell cell, IConfigRegistry configRegistry, Object newValue) {
            cellColIdx = cell.getColumnIndex();
            return validate(cell.getColumnIndex(), cell.getRowIndex(), newValue);
        }

        @Override
        public boolean validate(int colIndex, int rowIndex, Object newValue) {
            log.trace("validate({}, {}, {}): start", rowIndex, colIndex, newValue);

            try {
                super.checkValidValue(newValue);

                baseValidator.cellColIdx = cellColIdx;

                StringTokenizer elementReader = new StringTokenizer((String) newValue, " \t\n\r\f,[]");
                while (elementReader.hasMoreTokens()) {
                    String nextToken = elementReader.nextToken();
                    baseValidator.validate(colIndex, rowIndex, nextToken);
                }
            }
            catch (Exception ex) {
                log.debug("validate({}, {}, {}): failed to validate: ", rowIndex, colIndex, newValue, ex);
                throw new ValidationFailedException(ex.getMessage(), ex);
            }
            finally {
                log.trace("validate({}, {}, {}): finish", rowIndex, colIndex, newValue);
            }

            return true;
        }

    }

    /*
     * NatTable DataValidator to validate entered input for a dataset with
     * a variable-length Datatype (note that this DataValidator should not
     * be used for String Datatypes that are variable-length).
     */
    private static class VlenDataValidator extends HDFDataValidator {

        private final HDFDataValidator baseValidator;

        VlenDataValidator(Datatype dtype) throws Exception {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(VlenDataValidator.class);

            log.trace("constructor: start");

            if (!dtype.isVLEN()) {
                log.debug("datatype is not a variable-length type");
                log.trace("constructor: finish");
                throw new Exception("VlenDataValidator: datatype is not a variable-length type");
            }

            Datatype baseType = dtype.getDatatypeBase();
            if (baseType == null) {
                log.debug("base datatype is null");
                log.trace("constructor: finish");
                throw new Exception("VlenDataValidator: base datatype is null");
            }

            log.trace("VlenDataValidator: base Datatype is {}", baseType.getDescription());

            try {
                baseValidator = getDataValidator(baseType);
            }
            catch (Exception ex) {
                log.debug("couldn't get DataValidator for base datatype: ", ex);
                log.trace("constructor: finish");
                throw new Exception("VlenDataValidator: couldn't get DataValidator for base datatype: " + ex.getMessage());
            }

            log.trace("constructor: finish");
        }

        @Override
        public boolean validate(ILayerCell cell, IConfigRegistry configRegistry, Object newValue) {
            cellColIdx = cell.getColumnIndex();
            return validate(cell.getColumnIndex(), cell.getRowIndex(), newValue);
        }

        @Override
        public boolean validate(int colIndex, int rowIndex, Object newValue) {
            log.trace("validate({}, {}, {}): start", rowIndex, colIndex, newValue);

            try {
                super.checkValidValue(newValue);

                baseValidator.cellColIdx = cellColIdx;

                StringTokenizer elementReader = new StringTokenizer((String) newValue, " \t\n\r\f,()");
                while (elementReader.hasMoreTokens()) {
                    String nextToken = elementReader.nextToken();
                    baseValidator.validate(colIndex, rowIndex, nextToken);
                }
            }
            catch (Exception ex) {
                log.debug("validate({}, {}, {}): failed to validate: ", rowIndex, colIndex, newValue, ex);
                throw new ValidationFailedException(ex.getMessage(), ex);
            }
            finally {
                log.trace("validate({}, {}, {}): finish", rowIndex, colIndex, newValue);
            }

            return true;
        }
    }

    /*
     * NatTable DataValidator to validate entered input for a dataset with a String
     * Datatype (including Strings of variable-length).
     */
    private static class StringDataValidator extends HDFDataValidator {

        private final Datatype datasetDatatype;

        StringDataValidator(final Datatype dtype) throws Exception {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(StringDataValidator.class);

            log.trace("constructor: start");

            if (!dtype.isString()) {
                log.debug("datatype is not a String type");
                log.trace("constructor: finish");
                throw new Exception("StringDataValidator: datatype is not a String type");
            }

            log.trace("StringDataValidator: base Datatype is {}", dtype.getDescription());

            this.datasetDatatype = dtype;

            log.trace("constructor: finish");
        }

        @Override
        public boolean validate(int colIndex, int rowIndex, Object newValue) {
            log.trace("validate({}, {}, {}): start", rowIndex, colIndex, newValue);

            try {
                super.checkValidValue(newValue);

                /*
                 * If this is a fixed-length string type, check to make sure that the data
                 * length does not exceed the datatype size.
                 */
                /*
                 * TODO: Add warning about overwriting NULL-terminator for NULLTERM type strings
                 */
                if (!datasetDatatype.isVarStr()) {
                    long lenDiff = ((String) newValue).length() - datasetDatatype.getDatatypeSize();

                    if (lenDiff > 0)
                        throw new Exception("string size larger than datatype size by " + lenDiff
                            + ((lenDiff > 1) ? " bytes." : " byte."));
                }
            }
            catch (Exception ex) {
                log.debug("validate({}, {}, {}): failed to validate: ", rowIndex, colIndex, newValue, ex);
                super.throwValidationFailedException(rowIndex, colIndex, newValue, ex.getMessage());
            }
            finally {
                log.trace("validate({}, {}, {}): finish", rowIndex, colIndex, newValue);
            }

            return true;
        }
    }

    private static class CharDataValidator extends HDFDataValidator {

        CharDataValidator(final Datatype dtype) {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(CharDataValidator.class);

            log.trace("constructor: start");

            log.trace("constructor: finish");
        }

    }

    /*
     * NatTable DataValidator to validate entered input for a dataset with
     * a numerical Datatype.
     */
    private static class NumericalDataValidator extends HDFDataValidator {

        private final Datatype datasetDatatype;

        NumericalDataValidator(Datatype dtype) throws Exception {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(NumericalDataValidator.class);

            log.trace("constructor: start");

            if (!dtype.isInteger() && !dtype.isFloat()) {
                log.debug("datatype is not an integer or floating-point type");
                log.trace("constructor: finish");
                throw new Exception("NumericalDataValidator: datatype is not an integer or floating-point type");
            }

            log.trace("NumericalDataValidator: base Datatype is {}", dtype.getDescription());

            this.datasetDatatype = dtype;

            log.trace("constructor: finish");
        }

        @Override
        public boolean validate(int colIndex, int rowIndex, Object newValue) {
            log.trace("validate({}, {}, {}): start", rowIndex, colIndex, newValue);

            try {
                super.checkValidValue(newValue);

                switch ((int) datasetDatatype.getDatatypeSize()) {
                    case 1:
                        if (datasetDatatype.isUnsigned()) {
                            /*
                             * First try to parse as a larger type in order to catch a NumberFormatException
                             */
                            Short shortValue = Short.parseShort((String) newValue);
                            if (shortValue < 0)
                                throw new NumberFormatException("Invalid negative value for unsigned datatype");

                            if (shortValue > (Byte.MAX_VALUE * 2) + 1)
                                throw new NumberFormatException("Value out of range. Value:\"" + newValue + "\"");
                        }
                        else {
                            Byte.parseByte((String) newValue);
                        }
                        break;

                    case 2:
                        if (datasetDatatype.isUnsigned()) {
                            /*
                             * First try to parse as a larger type in order to catch a NumberFormatException
                             */
                            Integer intValue = Integer.parseInt((String) newValue);
                            if (intValue < 0)
                                throw new NumberFormatException("Invalid negative value for unsigned datatype");

                            if (intValue > (Short.MAX_VALUE * 2) + 1)
                                throw new NumberFormatException("Value out of range. Value:\"" + newValue + "\"");
                        }
                        else {
                            Short.parseShort((String) newValue);
                        }
                        break;

                    case 4:
                        if (datasetDatatype.isInteger()) {
                            if (datasetDatatype.isUnsigned()) {
                                /*
                                 * First try to parse as a larger type in order to catch a NumberFormatException
                                 */
                                Long longValue = Long.parseLong((String) newValue);
                                if (longValue < 0)
                                    throw new NumberFormatException("Invalid negative value for unsigned datatype");

                                if (longValue > ((long) Integer.MAX_VALUE * 2) + 1)
                                    throw new NumberFormatException("Value out of range. Value:\"" + newValue + "\"");
                            }
                            else {
                                Integer.parseInt((String) newValue);
                            }
                        }
                        else {
                            /* Floating-point type */
                            Float.parseFloat((String) newValue);
                        }
                        break;

                    case 8:
                        if (datasetDatatype.isInteger()) {
                            if (datasetDatatype.isUnsigned()) {
                                /*
                                 * First try to parse as a larger type in order to catch a NumberFormatException
                                 */
                                BigInteger bigValue = new BigInteger((String) newValue);
                                if (bigValue.compareTo(BigInteger.ZERO) < 0)
                                    throw new NumberFormatException("Invalid negative value for unsigned datatype");

                                BigInteger maxRange = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)).add(BigInteger.valueOf(1));
                                if (bigValue.compareTo(maxRange) > 0)
                                    throw new NumberFormatException("Value out of range. Value:\"" + newValue + "\"");
                            }
                            else {
                                Long.parseLong((String) newValue);
                            }
                        }
                        else {
                            /* Floating-point type */
                            Double.parseDouble((String) newValue);
                        }
                        break;

                    default:
                        throw new ValidationFailedException("No validation logic for numerical data of size " + datasetDatatype.getDatatypeSize());
                }
            }
            catch (Exception ex) {
                super.throwValidationFailedException(rowIndex, colIndex, newValue, ex.toString());
            }
            finally {
                log.trace("validate({}, {}, {}): finish", rowIndex, colIndex, newValue);
            }

            return true;
        }
    }

    private static class EnumDataValidator extends HDFDataValidator {

        EnumDataValidator(final Datatype dtype) {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(EnumDataValidator.class);

            log.trace("constructor: start");

            log.trace("constructor: finish");
        }

    }

    private static class BitfieldDataValidator extends HDFDataValidator {

        BitfieldDataValidator(final Datatype dtype) {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(BitfieldDataValidator.class);

            log.trace("constructor: start");

            log.trace("constructor: finish");
        }

    }

    private static class RefDataValidator extends HDFDataValidator {

        RefDataValidator(final Datatype dtype) {
            super(dtype);

            log = org.slf4j.LoggerFactory.getLogger(RefDataValidator.class);

            log.trace("constructor: start");

            log.trace("constructor: finish");
        }

    }

}
