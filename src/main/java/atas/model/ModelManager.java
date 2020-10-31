package atas.model;

import static atas.commons.util.CollectionUtil.requireAllNonNull;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.logging.Logger;

import atas.commons.core.GuiSettings;
import atas.commons.core.LogsCenter;
import atas.commons.core.index.Index;
import atas.commons.core.random.RandomGenerator;
import atas.model.memo.Memo;
import atas.model.session.Attributes;
import atas.model.session.IndexRange;
import atas.model.session.ReadOnlySessionList;
import atas.model.session.Session;
import atas.model.session.SessionList;
import atas.model.session.SessionName;
import atas.model.session.VersionedAttributesList;
import atas.model.session.VersionedSessionList;
import atas.model.student.ReadOnlyStudentList;
import atas.model.student.Student;
import atas.model.student.StudentList;
import atas.model.student.VersionedStudentList;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/**
 * Represents the in-memory model of the student list, session list, userPrefs and memo data.
 */
public class ModelManager implements Model {
    private static final Logger logger = LogsCenter.getLogger(ModelManager.class);

    private final VersionedStudentList studentList;
    private final VersionedSessionList sessionList;
    private final VersionedAttributesList attributesList;
    private final UserPrefs userPrefs;
    private final FilteredList<Student> filteredStudents;
    private final FilteredList<Session> filteredSessions;
    private Index sessionId;
    private boolean isCurrentSessionEnabled;
    private final Memo memo;
    private final RandomGenerator rng;

    /**
     * Initializes a ModelManager with the given sessionList, studentList, userPrefs and memo content.
     */
    public ModelManager(ReadOnlySessionList sessionList, ReadOnlyStudentList studentList,
                        ReadOnlyUserPrefs userPrefs, String memoContent) {
        super();
        requireAllNonNull(studentList, userPrefs);

        logger.fine("Initializing with student list: " + studentList + " and user prefs " + userPrefs);

        this.sessionList = new VersionedSessionList(sessionList);
        this.studentList = new VersionedStudentList(studentList);
        this.attributesList = new VersionedAttributesList();
        this.userPrefs = new UserPrefs(userPrefs);
        this.filteredStudents = new FilteredList<>(this.studentList.getStudentList());
        this.filteredSessions = new FilteredList<>(this.sessionList.getSessions());
        this.memo = new Memo(memoContent);
        this.rng = RandomGenerator.makeRandomGenerator();
        isCurrentSessionEnabled = false;
    }

    public ModelManager() {
        this(new SessionList(), new StudentList(), new UserPrefs(), "");
    }

    @Override
    public boolean equals(Object obj) {
        // short circuit if same object
        if (obj == this) {
            return true;
        }

        // instanceof handles nulls
        if (!(obj instanceof ModelManager)) {
            return false;
        }

        // state check
        ModelManager other = (ModelManager) obj;
        return studentList.equals(other.studentList)
                && userPrefs.equals(other.userPrefs)
                && filteredStudents.equals(other.filteredStudents)
                && sessionList.equals(other.sessionList)
                && filteredSessions.equals(other.filteredSessions)
                && memo.equals(other.memo);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n" + "- student list: " + studentList + "\n");
        sb.append("- filtered session list: " + filteredStudents + "\n");
        sb.append("- session list: " + sessionList + "\n");
        sb.append("- filtered sessions: " + filteredSessions + "\n");
        sb.append("- memo: " + memo + "\n");
        return sb.toString();
    }

    //=========== UserPrefs ==================================================================================

    @Override
    public void setUserPrefs(ReadOnlyUserPrefs userPrefs) {
        requireNonNull(userPrefs);
        this.userPrefs.resetData(userPrefs);
    }

    @Override
    public ReadOnlyUserPrefs getUserPrefs() {
        return userPrefs;
    }

    @Override
    public GuiSettings getGuiSettings() {
        return userPrefs.getGuiSettings();
    }

    @Override
    public void setGuiSettings(GuiSettings guiSettings) {
        requireNonNull(guiSettings);
        userPrefs.setGuiSettings(guiSettings);
    }

    @Override
    public Path getStudentListFilePath() {
        return userPrefs.getStudentListFilePath();
    }

    @Override
    public void setStudentListFilePath(Path studentListFilePath) {
        requireNonNull(studentListFilePath);
        userPrefs.setStudentListFilePath(studentListFilePath);
    }

    //=========== StudentList ================================================================================

    @Override
    public void clearStudentList() {
        studentList.resetData(new StudentList());
        sessionList.updateStudentList(studentList.getStudentList());
        sessionList.updateAllSessionsAfterClear();
        updateCurrentAttributesList();
    }

    @Override
    public ReadOnlyStudentList getStudentList() {
        return studentList;
    }

    @Override
    public boolean hasStudent(Student student) {
        requireNonNull(student);
        return studentList.hasStudent(student);
    }

    @Override
    public void deleteStudent(Student target, Index id) {
        studentList.removeStudent(target);
        sessionList.updateStudentList(studentList.getStudentList());
        sessionList.updateAllSessionsAfterDelete(id);
        updateCurrentAttributesList();
        refreshSessionStatistics();
    }

    @Override
    public void addStudent(Student student) {
        studentList.addStudent(student);
        updateFilteredStudentList(PREDICATE_SHOW_ALL_STUDENTS);
        sessionList.updateStudentList(studentList.getStudentList());
        sessionList.updateAllSessionsAfterAdd();
        updateCurrentAttributesList();
        refreshSessionStatistics();
    }

    @Override
    public void setStudent(Student target, Student editedStudent) {
        requireAllNonNull(target, editedStudent);

        studentList.setStudent(target, editedStudent);
        sessionList.updateStudentList(studentList.getStudentList());
        updateCurrentAttributesList();
    }

    //=========== SessionList ================================================================================


    @Override
    public void resetSessionList() {
        this.sessionList.clearSessions();
        sessionId = null;
    }

    @Override
    public SessionList getSessionList() {
        return this.sessionList;
    }

    @Override
    public Path getSessionListFilePath() {
        return userPrefs.getSessionListFilePath();
    }

    @Override
    public void setSessionListFilePath(Path sessionListFilePath) {
        requireNonNull(sessionListFilePath);
        userPrefs.setSessionListFilePath(sessionListFilePath);
    }

    @Override
    public boolean hasSession(Session session) {
        requireNonNull(session);
        return sessionList.contains(session);
    }

    @Override
    public void deleteSession(Session target, Index id) {
        if (sessionId != null && id.getZeroBased() == sessionId.getZeroBased()) {
            sessionId = null;
        }
        sessionList.updateStudentList(studentList.getStudentList());
        sessionList.deleteSession(target);
        refreshStudentStatistics();
    }

    @Override
    public void addSession(Session session) {
        sessionList.updateStudentList(studentList.getStudentList());
        sessionList.addSession(session);
        updateFilteredSessionList(PREDICATE_SHOW_ALL_SESSIONS);
        refreshStudentStatistics();
    }

    @Override
    public void setSession(Session target, Session editedSession) {
        sessionList.setSession(target, editedSession);
    }

    @Override
    public void updateParticipationBySessionName(SessionName sessionName, IndexRange indexRange) {
        Session session = getCurrentSession();
        sessionName = session.getSessionName();

        sessionList.updateStudentParticipation(sessionName, indexRange);
        attributesList.setCurrentAttributeList(sessionName.value, session.getAttributeList());
        refreshStatistics();
    }

    @Override
    public void updatePresenceBySessionName(SessionName sessionName, IndexRange indexRange) {
        Session session = getCurrentSession();
        sessionName = session.getSessionName();

        sessionList.updateStudentPresence(sessionName, indexRange);
        attributesList.setCurrentAttributeList(sessionName.value, session.getAttributeList());
        refreshStatistics();
    }

    //=========== Current Session Attribute List =============================================================

    @Override
    public Session getCurrentSession() {
        assert sessionId != null : "Attempted to get current session when session ID is null";
        return sessionList.getSessionBasedOnId(sessionId);
    }

    @Override
    public ObservableList<Attributes> getCurrentAttributesList() {
        return attributesList.getCurrentAttributeList();
    }

    @Override
    public void updateCurrentAttributesList() {
        if (sessionId != null) {
            attributesList.resetData(getCurrentSession().getAttributeList());
        }
    }

    //=========== Filtered Student List Accessors =============================================================

    /**
     * Returns an unmodifiable view of the list of {@code Student} backed by the internal list of
     * {@code versionedStudentList}
     */
    @Override
    public ObservableList<Student> getFilteredStudentList() {
        return filteredStudents;
    }

    @Override
    public void updateFilteredStudentList(Predicate<Student> predicate) {
        requireNonNull(predicate);
        filteredStudents.setPredicate(predicate);
    }

    //=========== Filtered Session List Accessors =============================================================

    @Override
    public ObservableList<Session> getFilteredSessionList() {
        return filteredSessions;
    }

    @Override
    public void updateFilteredSessionList(Predicate<Session> predicate) {
        requireNonNull(predicate);
        filteredSessions.setPredicate(predicate);
    }

    //=========== Filtered Session Accessors =============================================================
    @Override
    public Index getSessionId() {
        return sessionId;
    }

    @Override
    public void enterSession(Index sessionId) {
        this.sessionId = sessionId;
        SessionName sessionName = sessionList.getSessionBasedOnId(sessionId).getSessionName();
        this.attributesList.setCurrentAttributeList(sessionName.value, getCurrentSession().getAttributeList());
        setCurrentSessionTrue();
    }

    @Override
    public void setCurrentSessionFalse() {
        this.isCurrentSessionEnabled = false;
    }

    @Override
    public void setCurrentSessionTrue() {
        this.isCurrentSessionEnabled = true;
    }

    @Override
    public boolean returnCurrentSessionEnabledStatus() {
        return isCurrentSessionEnabled;
    }

    @Override
    public String getLeftSessionDetails() {
        if (sessionId == null) {
            String nullSessionDetails = "Currently not in any session";
            return nullSessionDetails;
        } else {
            requireNonNull(sessionList);
            Session currentEnteredSession = getCurrentSession();
            requireNonNull(currentEnteredSession);
            String sessionName = currentEnteredSession.getSessionName().toString();
            String sessionDate = currentEnteredSession.getSessionDate().toString();
            return String.format("Current Session: %s   Date: %s", sessionName, sessionDate);
        }
    }

    @Override
    public String getRightSessionDetails() {
        if (sessionId == null) {
            String nullSessionDetails = "";
            return nullSessionDetails;
        } else {
            requireNonNull(sessionList);
            Session currentEnteredSession = getCurrentSession();
            requireNonNull(currentEnteredSession);
            String presenceStats = currentEnteredSession.getSessionStats()
                .getPresenceStatistics().getDataAsPercentage();
            String participationStats = currentEnteredSession.getSessionStats()
                .getParticipationStatistics().getDataAsPercentage();
            return String.format("%s    %s", presenceStats, participationStats);
        }
    }



    //=========== Memo ================================================================================

    @Override
    public Path getMemoFilePath() {
        return userPrefs.getMemoFilePath();
    }

    @Override
    public void setMemoFilePath(Path memoFilePath) {
        requireNonNull(memoFilePath);
        userPrefs.setMemoFilePath(memoFilePath);
    }

    @Override
    public Memo getMemo() {
        return memo;
    }

    @Override
    public String getMemoContent() {
        return memo.getContent();
    }

    @Override
    public void saveMemoContent(String content) {
        requireAllNonNull(content);
        memo.setContent(content);
    }

    @Override
    public void addNoteToMemo(String note) {
        requireNonNull(note);
        memo.addNote(note);
    }

    //=========== RandomGenerator =========================================================================

    @Override
    public Index generateRandomStudentIndex() {
        return rng.getNextIndex(filteredStudents.size());
    }

    //=========== Statistics ================================================================================

    @Override
    public void refreshStatistics() {
        refreshStudentStatistics();
        refreshSessionStatistics();
    }

    @Override
    public void refreshStudentStatistics() {
        studentList.refreshStudentListStatistics(sessionList);
        updateFilteredStudentList(x -> false);
        updateFilteredStudentList(x -> true);
    }

    @Override
    public void refreshSessionStatistics() {
        sessionList.refreshSessionListStatistics();
        updateFilteredSessionList(x -> false);
        updateFilteredSessionList(x -> true);
    }

    //=========== Undo/Redo =========================================================================

    @Override
    public void commit() {
        studentList.commit();
        sessionList.commit();
        attributesList.commit();
    }

    @Override
    public boolean canUndo() {
        return studentList.canUndo()
                && sessionList.canUndo()
                && attributesList.canUndo();
    }

    @Override
    public void undo() {
        studentList.undo();
        sessionList.undo();
        attributesList.undo();
        refreshStatistics();
    }

    @Override
    public boolean canRedo() {
        return studentList.canRedo()
                && sessionList.canRedo()
                && attributesList.canRedo();
    }

    @Override
    public void redo() {
        studentList.redo();
        sessionList.redo();
        attributesList.redo();
        refreshStatistics();
    }
}
