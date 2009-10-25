import java.lang.*;
import java.util.*;
import java.io.*;

public class ParticleSet {
    public Set<String> particleName = new HashSet<String>();

    // method to convert names to particles
    Set<Particle> getParticles(Board board) {
	Set<Particle> ps = new HashSet<Particle>();
	for (Iterator<String> e = particleName.iterator(); e.hasNext() ;)
	    ps.add(board.getOrCreateParticle(e.next()));
	return ps;
    }

    // i/o
    void toStream (OutputStream out) {
	PrintStream print = new PrintStream(out);
	for (Iterator<String> e = particleName.iterator(); e.hasNext() ;)
	    print.println (e.next());
	print.println ("END");
    }

    void toFile(String filename) {
	try {
	    FileOutputStream fos = new FileOutputStream(filename);
	    toStream (fos);
	    fos.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    static ParticleSet fromStream (InputStream in) {
	ParticleSet ps = new ParticleSet();
	InputStreamReader read = new InputStreamReader(in);
	BufferedReader buff = new BufferedReader(read);
	try {
	    while (buff.ready()) {
		String s = buff.readLine();
		if (s.equals("END"))
		    break;
		else
		    ps.particleName.add(s);
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return ps;
    }

    static ParticleSet fromFile (String filename) {
	try {
	    FileInputStream fis = new FileInputStream(filename);
	    return fromStream(fis);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }
}
