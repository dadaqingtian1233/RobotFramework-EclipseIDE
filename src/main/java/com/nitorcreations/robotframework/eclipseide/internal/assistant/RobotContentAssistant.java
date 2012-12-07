/**
 * Copyright 2012 Nitor Creations Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nitorcreations.robotframework.eclipseide.internal.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import com.nitorcreations.robotframework.eclipseide.builder.parser.RobotFile;
import com.nitorcreations.robotframework.eclipseide.builder.parser.RobotLine;
import com.nitorcreations.robotframework.eclipseide.editors.ResourceManager;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString.ArgumentType;

public class RobotContentAssistant implements IContentAssistProcessor {

    private final IProposalGenerator proposalGenerator;

    public RobotContentAssistant(IProposalGenerator proposalGenerator) {
        this.proposalGenerator = proposalGenerator;
    }

    // ctrl-space completion proposals
    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int documentOffset) {
        // find info about current line
        int lineNo;
        IDocument document = viewer.getDocument();
        try {
            lineNo = document.getLineOfOffset(documentOffset);
        } catch (BadLocationException ex) {
            return null;
        }

        List<RobotLine> lines = RobotFile.get(document).getLines();
        RobotLine robotLine;
        if (lineNo < lines.size()) {
            robotLine = lines.get(lineNo);
        } else {
            robotLine = new RobotLine(lineNo, documentOffset, Collections.<ParsedString> emptyList());
        }
        ParsedString argument = robotLine.getArgumentAt(documentOffset);
        if (argument == null) {
            argument = synthesizeArgument(document, documentOffset, lineNo);
        }

        IFile file = ResourceManager.resolveFileFor(document);
        List<RobotCompletionProposal> proposals = new ArrayList<RobotCompletionProposal>();
        boolean allowKeywords = false;
        boolean allowVariables = false;
        switch (argument.getType()) {
            case KEYWORD_CALL:
                allowKeywords = true;
                break;
            case KEYWORD_CALL_DYNAMIC:
                allowKeywords = true;
                allowVariables = true;
                break;
            case KEYWORD_ARG:
            case SETTING_FILE_ARG:
            case SETTING_VAL:
            case SETTING_FILE:
            case VARIABLE_VAL: // TODO only suggest local variables
                allowVariables = true;
                break;
        }
        if (allowKeywords) {
            proposalGenerator.addKeywordProposals(file, argument, documentOffset, proposals);
        }
        if (allowVariables) {
            proposalGenerator.addVariableProposals(file, argument, documentOffset, proposals);
        }
        if (proposals.isEmpty()) {
            return null;
        }
        return proposals.toArray(new ICompletionProposal[proposals.size()]);
    }

    /**
     * Since there is no argument for the current cursor position (otherwise this method wouldn't have been called),
     * figure out which argument it would be by fake-inserting a dummy character at that position. After parsing the
     * file with the dummy character included, grab the argument that now resolves for the cursor position. Then undo
     * the added dummy character from that argument and return the resulting argument, which is possibly empty, but
     * which has a suitable {@link ArgumentType} assigned to it. This type thus indicates what type the argument would
     * be should the user choose to use any of the content assist suggestions, and lets us decide what content assist
     * suggestions to show in the first place.
     * 
     * @return the synthesized argument
     */
    private ParsedString synthesizeArgument(IDocument document, int documentOffset, int lineNo) {
        String documentText = document.get();
        StringBuilder newText = new StringBuilder(documentText.length() + 3);
        newText.append(documentText, 0, documentOffset);
        newText.append('x'); // dummy character
        newText.append(documentText, documentOffset, documentText.length());
        List<RobotLine> lines = RobotFile.parse(newText.toString()).getLines();
        RobotLine robotLine = lines.get(lineNo);
        ParsedString synthesizedArgument = robotLine.getArgumentAt(documentOffset);
        assert synthesizedArgument != null;
        assert synthesizedArgument.getArgCharPos() == documentOffset;
        String synthesizedArgumentWithoutDummyCharacter = synthesizedArgument.getValue().substring(1);
        ParsedString actualArgument = new ParsedString(synthesizedArgumentWithoutDummyCharacter, documentOffset);
        actualArgument.copyTypeVariablesFrom(synthesizedArgument);
        return actualArgument;
    }

    // ctrl-shift-space information popups
    @Override
    public IContextInformation[] computeContextInformation(ITextViewer viewer, int documentOffset) {
        return null;
        // TODO example code below
        // IContextInformation[] result = new IContextInformation[5];
        // for (int i = 0; i < result.length; i++) {
        // String contextDisplayString = "contextDisplayString " + i;
        // String informationDisplayString = "informationDisplayString " + i;
        // result[i] = new ContextInformation(contextDisplayString, informationDisplayString);
        // }
        // return result;
    }

    @Override
    public IContextInformationValidator getContextInformationValidator() {
        return new IContextInformationValidator() {

            private int offset;

            @Override
            public void install(IContextInformation info, ITextViewer viewer, int offset) {
                this.offset = offset;
            }

            @Override
            public boolean isContextInformationValid(int offset) {
                // TODO return false when cursor goes out of context for the
                // IContextInformation given to install()
                // see ContextInformationValidator.isContextInformationValid()

                // the user can always close a shown IContextInformation
                // instance by hitting Esc or moving the focus out of eclipse

                // if the previous IContextInformation is not closed before the
                // next is shown, it is temporarily hidden until the next one is
                // closed. This might confuse the user.
                return this.offset == offset;
            }
        };
    }

    @Override
    public char[] getCompletionProposalAutoActivationCharacters() {
        return new char[] { '$', '@' };
    }

    @Override
    public char[] getContextInformationAutoActivationCharacters() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

}
