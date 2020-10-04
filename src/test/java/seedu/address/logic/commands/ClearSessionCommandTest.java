package seedu.address.logic.commands;

import static seedu.address.logic.commands.CommandTestUtil.assertCommandSuccess;

import org.junit.jupiter.api.Test;

import seedu.address.model.Model;
import seedu.address.model.ModelManager;
import seedu.address.testutil.ModelManagerBuilder;

public class ClearSessionCommandTest {

    @Test
    public void execute_emptySessionList_success() {
        Model model = new ModelManager();
        Model expectedModel = new ModelManager();

        assertCommandSuccess(new ClearSessionCommand(), model, ClearSessionCommand.MESSAGE_SUCCESS, expectedModel);
    }

    @Test
    public void execute_nonEmptySessionList_success() {
        Model model = ModelManagerBuilder.buildTypicalModelManager();
        Model expectedModel = ModelManagerBuilder.buildTypicalModelManager();
        expectedModel.resetSessionList();

        assertCommandSuccess(new ClearSessionCommand(), model, ClearSessionCommand.MESSAGE_SUCCESS, expectedModel);
    }
}