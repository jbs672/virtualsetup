package com.example.desktopenv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Catalogo em memoria de programas FantasyISA. */
public class FantasyProgramStore {
    private final List<FantasyProgram> programs = new ArrayList<FantasyProgram>();

    public synchronized void addProgram(FantasyProgram program) {
        if (program == null) throw new IllegalArgumentException("Programa nulo.");
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).getName().equals(program.getName())) {
                programs.set(i, program);
                return;
            }
        }
        programs.add(program);
    }

    public synchronized FantasyProgram findProgram(String name) {
        if (name == null) return null;
        int i;
        for (i = 0; i < programs.size(); i++) {
            FantasyProgram p = programs.get(i);
            if (name.equals(p.getName())) return p;
        }
        return null;
    }

    public synchronized boolean removeProgram(String name) {
        if (name == null) return false;
        int i;
        for (i = 0; i < programs.size(); i++) {
            if (name.equals(programs.get(i).getName())) {
                programs.remove(i);
                return true;
            }
        }
        return false;
    }

    public synchronized List<FantasyProgram> getPrograms() {
        return Collections.unmodifiableList(new ArrayList<FantasyProgram>(programs));
    }
}
