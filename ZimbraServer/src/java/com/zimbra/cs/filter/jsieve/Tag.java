/*
 * 
 */

/*
 * Created on Nov 1, 2004
 *
 */
package com.zimbra.cs.filter.jsieve;

import java.util.List;

import org.apache.jsieve.Argument;
import org.apache.jsieve.Arguments;
import org.apache.jsieve.Block;
import org.apache.jsieve.SieveContext;
import org.apache.jsieve.exception.SieveException;
import org.apache.jsieve.StringListArgument;
import org.apache.jsieve.exception.SyntaxException;
import org.apache.jsieve.commands.AbstractActionCommand;
import org.apache.jsieve.mail.MailAdapter;

public class Tag extends AbstractActionCommand {

    @Override
    protected Object executeBasic(MailAdapter mail, Arguments args, Block block, SieveContext context) {
        String tagName =
            (String) ((StringListArgument) args.getArgumentList().get(0))
                .getList().get(0);

        // Only one tag with the same tag name allowed, others should be
        // discarded?            
        
        mail.addAction(new ActionTag(tagName));

        return null;
    }

    @Override
    protected void validateArguments(Arguments arguments, SieveContext context)
    throws SieveException
    {
        @SuppressWarnings("unchecked")
        List<Argument> args = arguments.getArgumentList();
        if (args.size() != 1)
            throw new SyntaxException(
                "Exactly 1 argument permitted. Found " + args.size());

        Object argument = args.get(0);
        if (!(argument instanceof StringListArgument))
            throw new SyntaxException("Expecting a string-list");

        if (1 != ((StringListArgument) argument).getList().size())
            throw new SyntaxException("Expecting exactly one argument");
    }
}
