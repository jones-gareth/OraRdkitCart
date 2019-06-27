package com.cairn.rmi.task;

import com.cairn.common.ModelException;
import com.cairn.common.RDKitOps;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Map;

/**
 * Uses RDKit to translate between structural formats.
 * <p>
 * The setting object should be a hash with String keys:
 * <p>
 * fromFormat: value is a string describing the input structure format
 * <p>
 * toFormat: value is a string describing the input output format.
 * <p>
 * includeTitle: value is a boolean describing whether to keep structure name in
 * output smiles (optional).
 * <p>
 * input: value is a string or list.
 * <p>
 * The results object contains: In the input string case the result is a string
 * of the translated single input structure. In the input List case the result
 * is a List of translated molecules.
 *
 * @author Gareth Jones
 */
public class TranslateTask extends AbstractTask {

    private static final long serialVersionUID = 1000L;

    private static final Logger logger = Logger.getLogger(TranslateTask.class.getName());


    @Override
    public Object submitTask() {

        Map<?, ?> parameters = (Map<?, ?>) settings;
        String fromFmtString = (String) parameters.get("fromFormat");
        String toFmtString = (String) parameters.get("toFormat");
        logger.debug("From format is " + fromFmtString + " to format is " + toFmtString);
        Object input = parameters.get("input");
        var inputFormat = RDKitOps.MolFormat.fromString(fromFmtString);
        var outputFormat = RDKitOps.MolFormat.fromString(toFmtString);
        if (input instanceof String) {
            // single compound
            String inputString = (String) input;
            logger.debug("single string input is " + input);
            results = convertMolecules(new String[]{inputString}, inputFormat,
                    outputFormat)[0];
        } else {
            // multiple compounds
            String[] inputMolecules = (String[]) input;
            logger.debug("Multiple compound input is "
                    + StringUtils.join(inputMolecules, "\n"));
            results = convertMolecules(inputMolecules, inputFormat, outputFormat);
        }

        return results;
    }

    private static String[] convertMolecules(String[] inputs, RDKitOps.MolFormat fromFormat, RDKitOps.MolFormat toFormat) {
        return Arrays.stream(inputs)
                .map(input ->
                        RDKitOps.stringToMolecule(input, fromFormat)
                                .map(mol -> RDKitOps.moleculeToString(mol, toFormat))
                                .orElseThrow(() ->
                                        new ModelException("Unable to convert structure " + input + " format " + fromFormat + " to molecule"))
                )
                .toArray(String[]::new);
    }
}
